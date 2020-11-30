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

import io.netty.util.AttributeKey;

import org.apache.skywalking.apm.agent.core.context.AbstractTracerContext;
import org.apache.skywalking.apm.network.trace.component.Component;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;

public class Constants {
    public static final AttributeKey<AbstractTracerContext> KEY_CONTEXT = AttributeKey.valueOf("SW_CONTEXT");

    public static final Component COMPONENT_NETTY_HTTP_SERVER = ComponentsDefine.TOMCAT;
    public static final Component COMPONENT_NETTY_HTTP_CLIENT = ComponentsDefine.HTTPCLIENT;
}
