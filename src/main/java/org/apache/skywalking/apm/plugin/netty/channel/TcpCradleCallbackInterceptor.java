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

import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Method;

import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceConstructorInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;

import com.tencent.cradle.message.tcp.CradleCommonMessage;
import com.tencent.cradle.message.tcp.TcpResponse;

public class TcpCradleCallbackInterceptor implements InstanceMethodsAroundInterceptor,
        InstanceConstructorInterceptor {

    private static final ILog LOG = LogManager.getLogger(TcpCradleCallbackInterceptor.class);

    @Override
    public void onConstruct(EnhancedInstance objInst, Object[] allArguments) throws Throwable {
        objInst.setSkyWalkingDynamicField(allArguments[0]);
    }

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, MethodInterceptResult result) throws Throwable {
        long start = System.currentTimeMillis();
        ChannelHandlerContext context = (ChannelHandlerContext) objInst.getSkyWalkingDynamicField();
        TcpResponse response = (TcpResponse) allArguments[0];
        CradleCommonMessage msg = response.getMessage();
        TracingHelper.onServerResponse(msg, context);
        LOG.debug("callback onComplete cost: {}ms", System.currentTimeMillis() - start);
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments,
            Class<?>[] argumentsTypes, Object ret) throws Throwable {
        return ret;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method,
            Object[] allArguments, Class<?>[] argumentsTypes, Throwable t) {

    }
}
