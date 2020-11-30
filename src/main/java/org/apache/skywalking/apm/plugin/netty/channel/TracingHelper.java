/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.netty.channel;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.skywalking.apm.agent.core.context.AbstractTracerContext;
import org.apache.skywalking.apm.agent.core.context.CarrierItem;
import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.tag.Tags;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;

import com.tencent.cradle.message.tcp.CradleCommonMessage;
import com.tencent.cradle.service.ResultCode;
import com.tencent.cradle.skywalking.ContextCarrierParser;
import com.tencent.infra.route.tcp.common.ByteBufUtils;

public class TracingHelper {

    private static final ILog LOG = LogManager.getLogger(TracingHelper.class);

    private static final String DEFAULT_SERVICE = "Cradle";

    public static AbstractTracerContext getTracingContext() {
        if (ContextManager.isActive()) {
            try {
                Field f = ContextManager.class.getDeclaredField("CONTEXT");
                f.setAccessible(true);
                ThreadLocal<AbstractTracerContext> context = (ThreadLocal<AbstractTracerContext>) f
                        .get(ContextManager.class);
                return context.get();
            } catch (Throwable e) {
                LOG.error(e.getMessage(), e);
            }
        }
        return null;
    }

    public static void setTracingContext(AbstractTracerContext context) {
        try {
            Field f = ContextManager.class.getDeclaredField("CONTEXT");
            f.setAccessible(true);
            ThreadLocal<AbstractTracerContext> cxt = (ThreadLocal<AbstractTracerContext>) f
                    .get(ContextManager.class);
            cxt.set(context);
        } catch (Throwable e) {
            LOG.error(e.getMessage(), e);
        }
    }

    public static void skipData(ByteBuf buf) {
        int len = buf.readInt();
        if (len > 0) {
            buf.skipBytes(len);
        }
    }

    public static void onServerRequest(ByteBuf buf, ChannelHandlerContext context) {
        LOG.debug("handle request");
        //cradle handle request
        String service = decodeService(buf);
        if (StringUtils.isEmpty(service)) {
            service = DEFAULT_SERVICE;
        }
        buf.skipBytes(4); // retCode
        skipData(buf); //body
        if (buf.readableBytes() == 0) {
            LOG.debug("handle request readable bytes 0");
            return;
        }
        if (ContextManager.isActive()) {
            setTracingContext(null);
        }
        // parse skywalking context.
        int contextLen = buf.readInt();
        LOG.debug("context data length: {}", contextLen);
        byte[] data = new byte[contextLen];
        buf.readBytes(data);
        Map<String, String> contextMap = ContextCarrierParser.decode(data);
        ContextCarrier contextCarrier = new ContextCarrier();
        CarrierItem next = contextCarrier.items();
        while (next.hasNext()) {
            next = next.next();
            next.setHeadValue(contextMap.get(next.getHeadKey()));
        }
        AbstractSpan span = ContextManager.createEntrySpan(service, contextCarrier);
        SpanLayer.asRPCFramework(span);
        context.channel().attr(Constants.KEY_CONTEXT).set(getTracingContext());
    }

    public static void onServerResponse(CradleCommonMessage msg, ChannelHandlerContext context) {
        //cradle send response
        LOG.debug("send response.");
        AbstractTracerContext tracingContext = context.channel().attr(Constants.KEY_CONTEXT).get();
        if (tracingContext == null) {
            return;
        }
        AbstractSpan span = tracingContext.activeSpan();
        int retCode = msg.getRetCode();
        if (retCode != ResultCode.OK.getCode()) {
            span.errorOccurred();
        }
        Tags.STATUS_CODE.set(span, String.valueOf(retCode));
        tracingContext.stopSpan(span);
    }

    public static void onClientRequest(ByteBuf buf, ChannelHandlerContext context) {
        LOG.debug("send request.");
        //cradle send request
        AbstractTracerContext tracingContext = context.channel().attr(Constants.KEY_CONTEXT).get();
        ContextCarrier contextCarrier = new ContextCarrier();
        String service = decodeService(buf);
        buf.resetReaderIndex();
        if (StringUtils.isEmpty(service)) {
            LOG.error("decode service error.");
            service = DEFAULT_SERVICE;
        }
        String remoteAddress = String.valueOf(context.channel().remoteAddress());
        AbstractSpan span = null;
        if (tracingContext != null) {
            span = tracingContext.createExitSpan(service,
                    remoteAddress.charAt(0) == '/' ? remoteAddress.substring(1) : remoteAddress);
            tracingContext.inject(contextCarrier);
        } else {
            if (ContextManager.isActive()) {
                setTracingContext(null);
            }
            span = ContextManager.createExitSpan(service, contextCarrier,
                    remoteAddress.charAt(0) == '/' ? remoteAddress.substring(1) : remoteAddress);
            context.channel().attr(Constants.KEY_CONTEXT).set(getTracingContext());
        }
        Tags.URL.set(span, service);
        SpanLayer.asRPCFramework(span);
        CarrierItem next = contextCarrier.items();
        Map<String, String> contextMap = new HashMap<>();
        while (next.hasNext()) {
            next = next.next();
            contextMap.put(next.getHeadKey(), next.getHeadValue());
        }
        byte[] data = ContextCarrierParser.encode(contextMap);
        int length = data.length;
        buf.writeInt(length).writeBytes(data);
        int totalLen = buf.readableBytes();
        buf.setInt(0, totalLen - 4);
        LOG.debug("total length: {}, write context data length: {}", totalLen, length);
    }

    public static void onClientResponse(ByteBuf buf, ChannelHandlerContext context) {
        //cradle handle response
        LOG.debug("handle response");
        AbstractTracerContext tracingContext = context.channel().attr(Constants.KEY_CONTEXT).get();
        if (tracingContext == null) {
            return;
        }
        AbstractSpan span = tracingContext.activeSpan();
        int retCode = decodeRetCode(buf);
        if (retCode != ResultCode.OK.getCode()) {
            span.errorOccurred();
        }
        Tags.STATUS_CODE.set(span, String.valueOf(retCode));
        tracingContext.stopSpan(span);
    }

    private static int decodeRetCode(ByteBuf buf) {
        buf.resetReaderIndex();
        buf.skipBytes(4) //total len
                .skipBytes(4) // cmdId
                .skipBytes(8) // seqId
                .skipBytes(4); // messageId
        skipData(buf); // traceId
        skipData(buf); // business
        skipData(buf); // service
        return buf.readInt(); // retCode
    }

    private static String decodeService(ByteBuf buf) {
        buf.resetReaderIndex();
        buf.skipBytes(4) //total len
                .skipBytes(4) // cmdId
                .skipBytes(8) // seqId
                .skipBytes(4); // messageId
        skipData(buf); // traceId
        skipData(buf); // business
        return ByteBufUtils.readString(buf);
    }
}
