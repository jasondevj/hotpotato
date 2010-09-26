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
import org.factor45.hotpotato.client.timeout.TimeoutManager;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.util.Arrays;
import java.util.concurrent.Executor;

/**
 * Non-pipelining implementation of {@link HttpConnection} interface.
 * <p/>
 * Since some server implementations aren't fully <a href="http://tools.ietf.org/html/rfc2616">RFC 2616</a> compliant
 * when it comes to pipelining, this is the default implementation.
 * <p/>
 * If you are sure the servers you will be connecting to support pipelining correctly, you can use the {@linkplain
 * PipeliningHttpConnection pipelining version}.
 * <p/>
 * This implementation only accepts one request at a time. If {@link
 * #execute(org.factor45.hotpotato.client.HttpRequestContext) execute()} is called while {@link #isAvailable()} would
 * return false, the request will be accepted and immediately fail with {@link HttpRequestFuture#EXECUTION_REJECTED}
 * <strong>unless</strong> the socket has been disconnected (in which case the request will not fail but
 * {@link #execute(HttpRequestContext)} will return {@code false} instead).
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class DefaultHttpConnection extends SimpleChannelUpstreamHandler implements HttpConnection {

    // configuration defaults -----------------------------------------------------------------------------------------

    private static final boolean DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST = false;
    private static final boolean RESTORE_NON_IDEMPOTENT_OPERATIONS = false;

    // configuration --------------------------------------------------------------------------------------------------

    private final String id;
    private final String host;
    private final int port;
    private final HttpConnectionListener listener;
    private final TimeoutManager timeoutManager;
    private final Executor executor;
    private boolean disconnectIfNonKeepAliveRequest;
    private boolean restoreNonIdempotentOperations;

    // internal vars --------------------------------------------------------------------------------------------------

    // Not using ReentrantReadWriteLock here since all locks would be write (mutex) locks.
    private final Object mutex;
    private volatile Channel channel;
    private volatile Throwable terminate;
    private boolean readingChunks;
    private volatile boolean available;
    private HttpRequestContext currentRequest;
    private HttpResponse currentResponse;
    private boolean discarding;

    // constructors ---------------------------------------------------------------------------------------------------

    public DefaultHttpConnection(String id, String host, int port, HttpConnectionListener listener,
                                 TimeoutManager timeoutManager) {
        this(id, host, port, listener, timeoutManager, null);
    }

    public DefaultHttpConnection(String id, String host, int port, HttpConnectionListener listener,
                                 TimeoutManager timeoutManager, Executor executor) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.listener = listener;
        this.timeoutManager = timeoutManager;
        this.executor = executor;
        this.mutex = new Object();
        this.disconnectIfNonKeepAliveRequest = DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST;
        this.restoreNonIdempotentOperations = RESTORE_NON_IDEMPOTENT_OPERATIONS;
    }

    // SimpleChannelUpstreamHandler -----------------------------------------------------------------------------------

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        // Synch this big block, as there is a chance that it's a complete response.
        // If it's a complete response (in other words, all the data necessary to mark the request as finished is
        // present), and it's cancelled meanwhile, synch'ing this block will guarantee that the request will
        // be marked as complete *before* being cancelled. Since DefaultHttpRequestFuture only allows 1 completion
        // event, the request will effectively be marked as complete, even though setFailure() will be called as
        // soon as this lock is released.
        // This synchronization is performed because of edge cases where the request is completing but nearly at
        // the same instant it times out, which causes another thread to issue a cancellation. With this locking in
        // place, the request will either cancel before this block executes or after - never during.
        synchronized (this.mutex) {
            if ((this.terminate != null) || (this.currentRequest == null)) {
                // Terminated or no current request; discard any incoming data as there is no one to feed it to.
                return;
            }

            if (!this.readingChunks) {
                HttpResponse response = (HttpResponse) e.getMessage();
                this.receivedResponseForCurrentRequest(response);

                // Chunked flag is set *even if chunk agreggation occurs*. When chunk aggregation occurs, the content
                // of the message will be present and flag will still be true so all conditions must be checked!
                if (response.isChunked() &&
                    ((response.getContent() == null) || (response.getContent().readableBytes() == 0))) {
                    this.readingChunks = true;
                } else {
                    ChannelBuffer content = response.getContent();
                    if (content.readable()) {
                        this.receivedContentForCurrentRequest(content, true);
                    }

                    // Non-chunked responses are always complete.
                    this.responseForCurrentRequestComplete();
                }
            } else {
                HttpChunk chunk = (HttpChunk) e.getMessage();
                if (chunk.isLast()) {
                    this.readingChunks = false;
                    // Same considerations as above. This lock will ensure the request will either be cancelled before
                    // this lock is obtained or the request will succeed (this is the last chunk, which means it's
                    // complete).
                    this.receivedContentForCurrentRequest(chunk.getContent(), true);
                    this.responseForCurrentRequestComplete();
                } else {
                    this.receivedContentForCurrentRequest(chunk.getContent(), false);
                }
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        if (this.channel == null) {
            return;
        }

        HttpRequestContext current = this.currentRequest;
        if (current != null) {
            current.getFuture().setFailure(e.getCause());
            this.listener.requestFinished(this, current);
        }

        if (this.channel.isConnected()) {
            this.channel.close();
        }
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        this.channel = e.getChannel();
        synchronized (this.mutex) {
            // Just in case terminate was issued meanwhile... will hardly ever happen.
            if (this.terminate != null) {
                return;
            }
            this.available = true;
        }
        this.listener.connectionOpened(this);
    }

    @Override
    public void channelDisconnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        HttpRequestContext request = null;
        synchronized (this.mutex) {
            if (this.terminate == null) {
                this.terminate = HttpRequestFuture.CONNECTION_LOST;
                this.available = false;
//                // Also, if there is a request currently executing mark it to be failed.
//                if (this.currentRequest != null) {
//                    this.currentRequest.getFuture().setFailure(this.terminate);
//                }
                // Commented the above block since if a request fails due to connection lost, it should be retried
                // in a different connection!
                request = this.currentRequest; // If this.currentRequest = null request will be null which is ok
            }
        }

        if ((request != null) && !request.getFuture().isDone() &&
            (request.isIdempotent() || this.restoreNonIdempotentOperations)) {
            this.listener.connectionTerminated(this, Arrays.asList(request));
        } else {
            this.listener.connectionTerminated(this);
        }
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

    @Override
    public void terminate() {
        synchronized (this.mutex) {
            // Already terminated, nothing to do here.
            if (this.terminate != null) {
                return;
            }

            this.terminate = HttpRequestFuture.SHUTTING_DOWN;
            // Mark as unavailable
            this.available = false;

            if (this.currentRequest != null) {
                this.currentRequest.getFuture().setFailure(this.terminate);
            }
        }

        if ((this.channel != null) && this.channel.isConnected()) {
            this.channel.close();
        }
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public boolean isAvailable() {
        return this.available;
    }

    @Override
    public boolean execute(final HttpRequestContext context) {
        if (context == null) {
            throw new IllegalArgumentException("HttpRequestContext cannot be null");
        }

        // Test for cancellation or tampering.
        if (context.getFuture().isDone()) {
            this.listener.requestFinished(this, context);
            return true;
        }

        synchronized (this.mutex) {
            // This implementation only allows one execution at a time. If requests are performed during the period in
            // which isAvailable() returns false, the request is immediately rejected.
            if (!this.available && (this.terminate == null) && this.channel.isConnected()) {
                // Terminate request wasn't issued, connection is open but unavailable, so fail the request!
                context.getFuture().setFailure(HttpRequestFuture.EXECUTION_REJECTED);
                this.listener.requestFinished(this, context);
                return true;
            } else if ((this.terminate != null) || !this.channel.isConnected()) {
                // Terminate was issued or channel is no longer connected, don't accept execution and leave request
                // untouched.
                return false;
            }

            // Mark connection as unavailable.
            this.available = false;
        }

        this.currentRequest = context;
        context.getFuture().markExecutionStart();

        // Now, it's theorethically possible that terminate() is called by other thread while this one is still "stuck"
        // (yes, I know write() is damn fast) on write() or the connection goes down while write() is still executing
        // and by some race-condition-miracle the events trigger faster than write() executes (hooray for imperative
        // multithreading!) thus causing write to fail. That's why this block is inside a try-catch. Y'never know...
        if (this.executor != null) {
            // Delegating writes to an executor results in lower throughput but also lower request/response time.
            this.executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        channel.write(context.getRequest());
                    } catch (Exception e) {
                        currentRequest = null;
                        context.getFuture().setFailure(e);
                        available = true;
                    }
                }
            });
        } else {
            // Otherwise just execute the write in the thread that called execute() (which typically is the client's
            // event dispatcher).
            try {
                this.channel.write(context.getRequest());
            } catch (Exception e) {
                // Some error occurred underneath, maybe ChannelClosedException or something like that.
                this.currentRequest = null;
                context.getFuture().setFailure(e);
                this.available = true;
                return true;
            }
        }

        // Launch a timeout checker.
        if (context.getTimeout() > 0) {
            this.timeoutManager.manageRequestTimeout(context);
        }

        return true;
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private void receivedContentForCurrentRequest(ChannelBuffer content, boolean last) {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.
        if (this.discarding) {
            return;
        }
        try {
            if (last) {
                this.currentRequest.getProcessor().addLastData(content);
            } else {
                this.currentRequest.getProcessor().addData(content);
            }
        } catch (Exception e) {
            // Unlock the future but don't signal that this connection is free just yet! There may still be contents
            // left to be consumed. Instead, set discarding flag to true.
            this.currentRequest.getFuture().setFailure(this.currentResponse, e);
            this.discarding = true;
        }
    }

    @SuppressWarnings({"unchecked"})
    private void responseForCurrentRequestComplete() {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.

        // Only unlock the future if the contents weren't being discarded. If the contents were being discarded, it
        // means that receivedResponseForCurrentRequest() already triggered the future!
        if (!this.discarding) {
            this.currentRequest.getFuture().setSuccess(this.currentRequest.getProcessor().getProcessedResponse(),
                                                       this.currentResponse);
        }

        this.currentRequestFinished();
    }

    /**
     * Handles the HttpReponse object received and possibly triggers the end of the current request. If the response
     * handler does not want to process the response, then the current request is finished immediately.
     *
     * @param response HttpResponse received
     */
    @SuppressWarnings({"unchecked"})
    private void receivedResponseForCurrentRequest(HttpResponse response) {
        // This method does not need any particular synchronization to ensure currentRequest doesn't change its state
        // to null during processing, since it's always called inside a synchronized() block.

        this.currentResponse = response;
        try {
            if (!this.currentRequest.getProcessor().willProcessResponse(response)) {
                // Rather than waiting for the full content to arrive (which will be discarded), perform an early
                // trigger on the Future, signalling request is finished. Note that currentRequestFinished() is *not*
                // called in this method, which means that execution of other requests will not be allowed, even though
                // the current request has been terminated. The reason for this is that all incoming data must be safely
                // consumed (and discarded) before another request hits the network - this avoids possible response data
                // mixing. When you *KNOW* for sure that the server supports HTTP/1.1 pipelining, then use the
                // pipelining implementation, PipeliningHttpConnection.

                // Even though the processor does not want to process the response, it might still return some default
                // result, so call getProcessedResponse() on it, rather than passing null to the Future.
                this.currentRequest.getFuture().setSuccess(this.currentRequest.getProcessor().getProcessedResponse(),
                                                           this.currentResponse);
                this.discarding = true;
            } else {
                // Response processor wants to process the contents of this request.
                this.discarding = false;
            }
        } catch (Exception e) {
            // Unlock the future but don't signal that this connection is free just yet! There may still be contents
            // left to be consumed. Instead, set discarding flag to true.
            this.currentRequest.getFuture().setFailure(response, e);
            this.discarding = true;
        }
    }

    private void currentRequestFinished() {
        // Always called inside a synchronized block.

        HttpRequestContext context = this.currentRequest;
        // Only signal as available if the connection will be kept alive and if terminate hasn't been issued AND
        // the channel is still connected.
        this.available = HttpHeaders.isKeepAlive(this.currentResponse) &&
                         this.terminate == null &&
                         this.channel.isConnected();

        this.currentRequest = null;
        this.currentResponse = null;

        this.listener.requestFinished(this, context);

        // Close non-keep alive connections if configured to do so...
        if (this.disconnectIfNonKeepAliveRequest && !this.available) {
            this.channel.close();
        }
    }

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

    public boolean isRestoreNonIdempotentOperations() {
        return restoreNonIdempotentOperations;
    }

    /**
     * Explicitly enables or disables recovery of non-idempotent operations when connections go down.
     * <p/>
     * When a connection goes down while executing a request it can restore that request by sending it back to the
     * listener inside the {@link HttpConnectionListener#connectionTerminated(HttpConnection, java.util.Collection)}
     * call.
     * <p/>
     * This can be dangerous for non-idempotent operations, because there is no guarantee that the request reached the
     * server and executed.
     * <p/>
     * By default, this option is disabled (safer).
     *
     * @param restoreNonIdempotentOperations Whether this {@code HttpConnection} should restore non-idempotent
     *                                       operations when the connection goes down.
     */
    public void setRestoreNonIdempotentOperations(boolean restoreNonIdempotentOperations) {
        this.restoreNonIdempotentOperations = restoreNonIdempotentOperations;
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        return new StringBuilder()
                .append("DefaultHttpConnection{")
                .append("id='").append(this.id).append('\'')
                .append('(').append(this.host).append(':').append(this.port)
                .append(")}").toString();
    }
}
