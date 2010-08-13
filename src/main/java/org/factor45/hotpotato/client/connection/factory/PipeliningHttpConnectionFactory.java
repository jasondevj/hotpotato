/*
 * Copyright 2010 Bruno de Carvalho
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.factor45.hotpotato.client.connection.factory;

import org.factor45.hotpotato.client.connection.HttpConnection;
import org.factor45.hotpotato.client.connection.HttpConnectionListener;
import org.factor45.hotpotato.client.timeout.TimeoutManager;

import java.util.concurrent.Executor;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class PipeliningHttpConnectionFactory implements HttpConnectionFactory {

    // configuration defaults -----------------------------------------------------------------------------------------

    private static final boolean DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST = false;
    private static final boolean ALLOW_POST_PIPELINING = false;

    // configuration --------------------------------------------------------------------------------------------------

    private boolean disconnectIfNonKeepAliveRequest;
    private boolean allowPostPipelining;

    // constructors ---------------------------------------------------------------------------------------------------

    public PipeliningHttpConnectionFactory() {
        this.disconnectIfNonKeepAliveRequest = DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST;
        this.allowPostPipelining = ALLOW_POST_PIPELINING;
    }

    // HttpConnectionFactory ------------------------------------------------------------------------------------------


    @Override
    public HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                        TimeoutManager manager) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                        TimeoutManager manager, Executor executor) {
        throw new UnsupportedOperationException("not implemented");
//        PipeliningHttpConnection connection = new PipeliningHttpConnection(id, host, port, listener,
//                                                                           delegateWritesToExecutor);
//        connection.setAllowPostPipelining(this.allowPostPipelining);
//        connection.setDisconnectIfNonKeepAliveRequest(this.disconnectIfNonKeepAliveRequest);
//        return connection;
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean isDisconnectIfNonKeepAliveRequest() {
        return disconnectIfNonKeepAliveRequest;
    }

    public void setDisconnectIfNonKeepAliveRequest(boolean disconnectIfNonKeepAliveRequest) {
        this.disconnectIfNonKeepAliveRequest = disconnectIfNonKeepAliveRequest;
    }

    public boolean isAllowPostPipelining() {
        return allowPostPipelining;
    }

    public void setAllowPostPipelining(boolean allowPostPipelining) {
        this.allowPostPipelining = allowPostPipelining;
    }
}
