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

package org.factor45.hotpotato.client.connection;

import org.factor45.hotpotato.client.HttpRequestContext;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.internal.ExecutorUtil;

import java.util.concurrent.Executor;

/**
 * Pipelining implementation of HttpConnection interface.
 * <p/>
 * This version accepts up N idempotent HTTP 1.1 operations. Should an error occur during the execution of the pipelined
 * requests, <strong>all</strong> the currently pipelined requests will fail - and will <strong>not</strong> be
 * retried.
 * <p/>
 * This version can yield faster throughput than {@link DefaultHttpConnection} implementation for bursts up to N
 * requests of idempotent HTTP 1.1 operations, since it will batch requests and receive batch responses, potentially
 * taking advantage of being able to fit several HTTP requests in the same TCP frame.
 * <p/>
 * When the limit of pipelined requests is hit, this connection presents the same behaviour as {@link
 * DefaultHttpConnection} - it starts returning {@code false} on {@code isAvailable()} and immediately failing any
 * requests that are submitted while {@code isAvailable()} would return {@code false}.
 * <p/>
 * Please note that if a connection to a host is likely to have all types of HTTP operations mixed (PUT, GET, POST, etc)
 * or also requests with version 1.0, this connection will not present any advantage over the default non-pipelining
 * implementation. Since it contains more complex logic, it will potentially be a little bit slower.
 * <p/>
 * Use this implementation when you know beforehand that you'll be performing mostly idempotent HTTP 1.1 operations.
 *
 * TODO implement Pipelining!
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class PipeliningHttpConnection extends SimpleChannelUpstreamHandler implements HttpConnection {

    // configuration defaults -----------------------------------------------------------------------------------------

    private static final boolean ALLOW_POST_PIPELINING = false;
    private static final boolean DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST = false;

    // configuration --------------------------------------------------------------------------------------------------

    private final String id;
    private final String host;
    private final int port;
    private final HttpConnectionListener listener;
    private boolean disconnectIfNonKeepAliveRequest;
    private boolean allowPostPipelining;

    // internal vars --------------------------------------------------------------------------------------------------

    private Channel channel;
    private volatile Throwable terminate;
    private HttpRequestContext currentRequest;
    private HttpResponse currentResponse;
    private boolean readingChunks;
    private final Object mutex;
    private volatile boolean available;
    private Executor executor;

    // constructors ---------------------------------------------------------------------------------------------------

    public PipeliningHttpConnection(String id, String host, int port, HttpConnectionListener listener,
                                    boolean delegateWritesToExecutor) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // SimpleChannelUpstreamHandler -----------------------------------------------------------------------------------

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        synchronized (this.mutex) {
            // Just in case terminate was issued meanwhile... will hardly ever happen unless someone is intentionally
            // trying to screw up...
            if (this.terminate == null) {
                this.channel = e.getChannel();
                this.available = true;
                this.listener.connectionOpened(this);
            }
        }
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        synchronized (this.mutex) {
            if (this.terminate == null) {
                this.terminate = HttpRequestFuture.CONNECTION_LOST;
                this.available = false;
                // Also, if there is a current request taking place mark it to be failed.
                // No need to lock the requestMutex here because Netty guarantees that only one ChannelHandler only
                // handles 1 event at a time.
                if (this.currentRequest != null) {
                    this.currentRequest.getFuture().setFailure(this.terminate);
                }
            }
        }

        this.listener.connectionTerminated(this);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (this.channel == null) {
            // No need for any extra steps since available only turns true when channel connects.
            // Simply notify the listener that the connection failed.
            this.listener.connectionFailed(this);
        }
    }

    // HttpConnection -------------------------------------------------------------------------------------------------

    public void terminate() {
        synchronized (this.mutex) {
            if (this.terminate != null) {
                return;
            }
            this.terminate = HttpRequestFuture.SHUTTING_DOWN;
            this.available = false;
        }

        ExecutorUtil.terminate(this.executor);
        if (this.channel.isConnected()) {
            this.channel.close();
        }
    }

    public String getId() {
        return this.id;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public boolean execute(HttpRequestContext context) {
        throw new UnsupportedOperationException("Not implemented");
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean isDisconnectIfNonKeepAliveRequest() {
        return disconnectIfNonKeepAliveRequest;
    }

    /**
     * Causes an explicit request to disconnect a channel after the response to a non-keepalive request is received.
     * <p/>
     * Use this only if the server you're connecting to doesn't follow standards and keeps HTTP/1.0 connections open or
     * doesn't close HTTP/1.1 connections with 'Connection: close' header.
     *
     * @param disconnectIfNonKeepAliveRequest
     *         Whether this {@code HttpConnection} should explicitly disconnect after executing a non-keepalive
     *         request.
     */
    public void setDisconnectIfNonKeepAliveRequest(boolean disconnectIfNonKeepAliveRequest) {
        this.disconnectIfNonKeepAliveRequest = disconnectIfNonKeepAliveRequest;
    }

    public boolean isAllowPostPipelining() {
        return allowPostPipelining;
    }

    public void setAllowPostPipelining(boolean allowPostPipelining) {
        this.allowPostPipelining = allowPostPipelining;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("PipeliningHttpConnection{")
                .append("id='").append(this.id).append('\'')
                .append('(').append(this.host).append(':').append(this.port)
                .append(")}").toString();
    }
}
