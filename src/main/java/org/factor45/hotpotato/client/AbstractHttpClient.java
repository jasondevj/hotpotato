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

package org.factor45.hotpotato.client;

import org.factor45.hotpotato.client.connection.HttpConnection;
import org.factor45.hotpotato.client.connection.HttpConnectionListener;
import org.factor45.hotpotato.client.connection.factory.DefaultHttpConnectionFactory;
import org.factor45.hotpotato.client.connection.factory.HttpConnectionFactory;
import org.factor45.hotpotato.client.event.ConnectionClosedEvent;
import org.factor45.hotpotato.client.event.ConnectionFailedEvent;
import org.factor45.hotpotato.client.event.ConnectionOpenEvent;
import org.factor45.hotpotato.client.event.EventType;
import org.factor45.hotpotato.client.event.ExecuteRequestEvent;
import org.factor45.hotpotato.client.event.HttpClientEvent;
import org.factor45.hotpotato.client.event.RequestCompleteEvent;
import org.factor45.hotpotato.client.host.HostContext;
import org.factor45.hotpotato.client.host.factory.DefaultHostContextFactory;
import org.factor45.hotpotato.client.host.factory.HostContextFactory;
import org.factor45.hotpotato.client.timeout.HashedWheelTimeoutManager;
import org.factor45.hotpotato.client.timeout.TimeoutManager;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.request.factory.DefaultHttpRequestFutureFactory;
import org.factor45.hotpotato.request.factory.HttpRequestFutureFactory;
import org.factor45.hotpotato.response.DiscardProcessor;
import org.factor45.hotpotato.response.HttpResponseProcessor;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.example.securechat.SecureChatSslContextFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.ExecutorUtil;

import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract implementation of the {@link HttpClient} interface. Contains most of the boilerplate code that other
 * {@link HttpClient} implementations would also need.
 * <p/>
 * This abstract implementation is in itself complete. If you extend this class, you needn't implement a single method
 * (just like {@link DefaultHttpClient} does). However you may want to override the specific behaviour of one or other
 * method, rather than reimplement the whole class all over again (just like {@link StatsGatheringHttpClient} does -
 * only overrides the {@code eventHandlingLoop()} method to gather execution statistics).
 * <p/>
 * <h3>Thread safety and performance</h3> This default implementation is thread-safe and, unlike <a
 * href="http://hc.apache.org/httpcomponents-client-4.0.1/index.html">Apache HttpClient</a>, the performance does not
 * degrade when the instance is shared by multiple threads accessing it at the same time.
 * <p/>
 * <h3>Event queue (producer/consumer)</h3> When this implementation is initialised, it fires up an auxilliary thread,
 * the consumer.
 * <p/>
 * Every time one of the variants of the method {@code execute()} is called, a new {@link HttpClientEvent} is generated
 * and introduced in a blocking event queue (on the caller's thread execution time). The consumer then grabs that
 * request and acts accordingly - it can either queue the request so that it is later executed in an available
 * connection, request a new connection in case no connections are available, directly execute this request, etc.
 * <p/>
 * <h3>Order</h3> By using an event queue, absolute order is guaranteed. If thread A calls {@code execute()} with a
 * request to host H prior to thread B (which also places a request for the same host), then the request provided by
 * thread A is guaranteed to be <strong>placed</strong> (i.e. written to the network) before the request placed by
 * thread B.
 * <p/>
 * This doesn't mean that request A will hit the server before request B or that the response for request A will arrive
 * before B. The reasons are obvious:
 *
 * <ul>
 *   <li>A can end up in a connection slower than B's</li>
 *   <li>Server can respond faster on one socket than on the other</li>
 *   <li>Response for request B can have 10b and for request A 10bKb</li>
 *   <li>etc</li>
 * </ul
 * <p/>
 * If you need to guarantee that a request B can only hit the server after a request A, you can either manually manage
 * that in your code through the {@link HttpRequestFuture} API or configure the concrete instance of this class to allow
 * at most 1 connection per host - although this last option will hurt performance globally.
 *
 * <div class="note">
 * <div class="header">Note:</div>
 * Calling {@linkplain #execute(String, int, HttpRequest, HttpResponseProcessor) one of the variants of {@code execute}}
 * with the client configured with {@linkplain #setAutoInflate(boolean) auto-inflation} turned on will cause a
 * 'ACCEPT_ENCODING' header to be added with value 'GZIP'.
 * </div>
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public abstract class AbstractHttpClient implements HttpClient, HttpConnectionListener {

    // constants ------------------------------------------------------------------------------------------------------

    protected static final InternalLogger LOG = InternalLoggerFactory.getInstance(AbstractHttpClient.class);
    protected static final HttpClientEvent POISON = new HttpClientEvent() {
        @Override
        public EventType getEventType() {
            return null;
        }

        @Override
        public String toString() {
            return "POISON";
        }
    };

    // configuration defaults -----------------------------------------------------------------------------------------

    protected static final boolean USE_SSL = false;
    protected static final int REQUEST_COMPRESSION_LEVEL = 0;
    protected static final boolean AUTO_INFLATE = false;
    protected static final int REQUEST_CHUNK_SIZE = 8192;
    protected static final boolean AGGREGATE_RESPONSE_CHUNKS = false;
    protected static final int CONNECTION_TIMEOUT_IN_MILLIS = 2000;
    protected static final int REQUEST_TIMEOUT_IN_MILLIS = 2000;
    protected static final int MAX_CONNECTIONS_PER_HOST = 3;
    protected static final int MAX_QUEUED_REQUESTS = Short.MAX_VALUE;
    protected static final boolean USE_NIO = false;
    protected static final int MAX_IO_WORKER_THREADS = 50;
    protected static final int MAX_EVENT_PROCESSOR_HELPER_THREADS = 20;
    protected static final boolean CLEANUP_INACTIVE_HOST_CONTEXTS = true;

    // configuration --------------------------------------------------------------------------------------------------

    protected boolean useSsl;
    protected int requestCompressionLevel;
    protected boolean autoInflate;
    protected int requestChunkSize;
    protected boolean aggregateResponseChunks;
    protected int maxConnectionsPerHost;
    protected int maxQueuedRequests;
    protected int connectionTimeoutInMillis;
    protected int requestTimeoutInMillis;
    protected boolean useNio;
    protected int maxIoWorkerThreads;
    protected int maxEventProcessorHelperThreads;
    protected HttpConnectionFactory connectionFactory;
    protected HostContextFactory hostContextFactory;
    protected HttpRequestFutureFactory futureFactory;
    protected TimeoutManager timeoutManager;
    protected boolean cleanupInactiveHostContexts;

    // internal vars --------------------------------------------------------------------------------------------------

    protected Executor executor;
    protected ChannelFactory channelFactory;
    protected ChannelPipelineFactory pipelineFactory;
    protected ChannelGroup channelGroup;
    protected BlockingQueue<HttpClientEvent> eventQueue;
    protected final Map<String, HostContext> contextMap;
    protected final AtomicInteger queuedRequests;
    protected int connectionCounter;
    protected CountDownLatch eventConsumerLatch;
    protected volatile boolean terminate;
    protected boolean internalTimeoutManager;

    // constructors ---------------------------------------------------------------------------------------------------

    public AbstractHttpClient() {
        this.useSsl = USE_SSL;
        this.requestCompressionLevel = REQUEST_COMPRESSION_LEVEL;
        this.autoInflate = AUTO_INFLATE;
        this.requestChunkSize = REQUEST_CHUNK_SIZE;
        this.aggregateResponseChunks = AGGREGATE_RESPONSE_CHUNKS;
        this.connectionTimeoutInMillis = CONNECTION_TIMEOUT_IN_MILLIS;
        this.requestTimeoutInMillis = REQUEST_TIMEOUT_IN_MILLIS;
        this.maxConnectionsPerHost = MAX_CONNECTIONS_PER_HOST;
        this.maxQueuedRequests = MAX_QUEUED_REQUESTS;
        this.useNio = USE_NIO;
        this.maxIoWorkerThreads = MAX_IO_WORKER_THREADS;
        this.maxEventProcessorHelperThreads = MAX_EVENT_PROCESSOR_HELPER_THREADS;
        this.cleanupInactiveHostContexts = CLEANUP_INACTIVE_HOST_CONTEXTS;

        this.queuedRequests = new AtomicInteger(0);

        // No need for synchronised structures here, as they'll be accessed by a single thread
        this.contextMap = new HashMap<String, HostContext>();
    }

    // HttpClient -----------------------------------------------------------------------------------------------------

    @Override
    public boolean init() {
        if (this.timeoutManager == null) {
            // Consumes less resources, puts less emphasis on precision.
            this.timeoutManager = new HashedWheelTimeoutManager();
            //this.timeoutManager = new BasicTimeoutManager(10);
            this.timeoutManager.init();
            this.internalTimeoutManager = true;
        }

        if (this.hostContextFactory == null) {
            this.hostContextFactory = new DefaultHostContextFactory();
        }
        if (this.connectionFactory == null) {
            this.connectionFactory = new DefaultHttpConnectionFactory();
        }
        if (this.futureFactory == null) {
            this.futureFactory = new DefaultHttpRequestFutureFactory();
        }

        this.eventConsumerLatch = new CountDownLatch(1);
        this.eventQueue = new LinkedBlockingQueue<HttpClientEvent>();

        // TODO instead of fixed size thread pool, use a cached thread pool with size limit (limited growth cached pool)
        this.executor = Executors.newFixedThreadPool(this.maxEventProcessorHelperThreads);
        Executor workerPool = Executors.newFixedThreadPool(this.maxIoWorkerThreads);

        if (this.useNio) {
            // It's only going to create 1 thread, so no harm done here.
            Executor bossPool = Executors.newCachedThreadPool();
            this.channelFactory = new NioClientSocketChannelFactory(bossPool, workerPool);
        } else {
            this.channelFactory = new OioClientSocketChannelFactory(workerPool);
        }

        this.channelGroup = new DefaultChannelGroup(this.toString());
        // Create a pipeline without the last handler (it will be added right before connecting).
        this.pipelineFactory = new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                if (useSsl) {
                    SSLEngine engine = SecureChatSslContextFactory.getServerContext().createSSLEngine();
                    engine.setUseClientMode(true);
                    pipeline.addLast("ssl", new SslHandler(engine));
                }

                if (requestCompressionLevel > 0) {
                    pipeline.addLast("deflater", new HttpContentCompressor(requestCompressionLevel));
                }

                pipeline.addLast("codec", new HttpClientCodec(4096, 8192, requestChunkSize));
                if (autoInflate) {
                    pipeline.addLast("inflater", new HttpContentDecompressor());
                }
                if (aggregateResponseChunks) {
                    pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
                }
                return pipeline;
            }
        };

        this.executor.execute(new Runnable() {
            @Override
            public void run() {
                eventHandlingLoop();
            }
        });
        return true;
    }

    @Override
    public void terminate() {
        if (this.terminate || this.eventQueue == null) {
            return;
        }

        // Stop accepting requests.
        this.terminate = true;
        // Copy any pending operations in order to signal execution request failures.
        Collection<HttpClientEvent> pendingEvents = new ArrayList<HttpClientEvent>(this.eventQueue);
        // Clear the queue and kill the consumer thread by "poisoning" the event queue.
        this.eventQueue.clear();
        this.eventQueue.add(POISON);
        try {
            this.eventConsumerLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Kill all connections (will cause failure on requests executing in those connections) and fail context-queued
        // requests.
        for (HostContext hostContext : this.contextMap.values()) {
            for (HttpRequestContext context : hostContext.getQueue()) {
                context.getFuture().setFailure(HttpRequestFuture.SHUTTING_DOWN);
            }
            for (HttpConnection connection : hostContext.getConnectionPool().getConnections()) {
                connection.terminate();
            }
        }
        this.contextMap.clear();

        // Fail all requests that were still in the event queue.
        for (HttpClientEvent event : pendingEvents) {
            if (event.getEventType() == EventType.EXECUTE_REQUEST) {
                ((ExecuteRequestEvent) event).getContext().getFuture().setFailure(HttpRequestFuture.SHUTTING_DOWN);
            }
        }

        try {
            this.channelGroup.close().await(1000);
        } catch (InterruptedException e) {
            Thread.interrupted();
        }

        this.channelFactory.releaseExternalResources();
        if (this.executor != null) {
            ExecutorUtil.terminate(this.executor);
        }

        if (this.internalTimeoutManager) {
            this.timeoutManager.terminate();
        }
    }

    @Override
    public <T> HttpRequestFuture<T> execute(String host, int port, HttpRequest request,
                                            HttpResponseProcessor<T> processor)
            throws CannotExecuteRequestException {
        return this.execute(host, port, this.requestTimeoutInMillis, request, processor);
    }

    @Override
    public HttpRequestFuture execute(String host, int port, HttpRequest request) throws CannotExecuteRequestException {
        return this.execute(host, port, request, DiscardProcessor.getInstance());
    }

    @Override
    public <T> HttpRequestFuture<T> execute(String host, int port, int timeout, HttpRequest request,
                                            HttpResponseProcessor<T> processor)
            throws CannotExecuteRequestException {
        if (this.eventQueue == null) {
            throw new CannotExecuteRequestException(this.getClass().getSimpleName() + " was not initialised");
        }

        if (this.queuedRequests.incrementAndGet() > this.maxQueuedRequests) {
            this.queuedRequests.decrementAndGet();
            throw new CannotExecuteRequestException("Request queue is full");
        }

        // Perform these checks on the caller thread's time rather than the event dispatcher's.
        if (this.autoInflate) {
            request.setHeader(HttpHeaders.Names.ACCEPT_ENCODING, HttpHeaders.Values.GZIP);
        }

        HttpRequestFuture<T> future = this.futureFactory.getFuture(true);
        HttpRequestContext<T> context = new HttpRequestContext<T>(host, port, timeout, request, processor, future);
        if (this.terminate || !this.eventQueue.offer(new ExecuteRequestEvent(context))) {
            throw new CannotExecuteRequestException("Failed to add request to queue");
        }

        return future;
    }

    @Override
    public boolean isHttps() {
        return this.useSsl;
    }

    // HttpConnectionListener -----------------------------------------------------------------------------------------

    @Override
    public void connectionOpened(HttpConnection connection) {
        if (this.terminate) {
            return;
        }
        this.eventQueue.offer(new ConnectionOpenEvent(connection));
    }

    @Override
    public void connectionTerminated(HttpConnection connection) {
        if (this.terminate) {
            return;
        }
        this.eventQueue.offer(new ConnectionClosedEvent(connection));
    }

    @Override
    public void connectionFailed(HttpConnection connection) {
        if (this.terminate) {
            return;
        }
        this.eventQueue.offer(new ConnectionFailedEvent(connection));
    }

    @Override
    public void requestFinished(HttpConnection connection, HttpRequestContext context) {
        if (this.terminate) {
            return;
        }
        this.eventQueue.offer(new RequestCompleteEvent(context));
    }

    // protected helpers ----------------------------------------------------------------------------------------------

    protected void eventHandlingLoop() {
        for (;;) {
            // Manual synchronisation here because before removing an element, we first need to check whether an
            // active available connection exists to satisfy the request.
            try {
                HttpClientEvent event = this.eventQueue.take();
                if (event == POISON) {
                    this.eventConsumerLatch.countDown();
                    return;
                }

                switch (event.getEventType()) {
                    case EXECUTE_REQUEST:
                        this.handleExecuteRequest((ExecuteRequestEvent) event);
                        break;
                    case REQUEST_COMPLETE:
                        this.handleRequestComplete((RequestCompleteEvent) event);
                        break;
                    case CONNECTION_OPEN:
                        this.handleConnectionOpen((ConnectionOpenEvent) event);
                        break;
                    case CONNECTION_CLOSED:
                        this.handleConnectionClosed((ConnectionClosedEvent) event);
                        break;
                    case CONNECTION_FAILED:
                        this.handleConnectionFailed((ConnectionFailedEvent) event);
                        break;
                    default:
                        // Consume and do nothing, unknown event.
                }
            } catch (InterruptedException e) {
                // ignore, poisoning the queue is the only way to stop
            }
        }
    }

    // private helpers --------------------------------------------------------------------------------------------

    protected void handleExecuteRequest(ExecuteRequestEvent event) {
        // First, add it to the queue (or create a queue for given host if one does not exist)
        String id = this.hostId(event.getContext());
        HostContext context = this.contextMap.get(id);
        if (context == null) {
            context = this.hostContextFactory
                    .createHostContext(event.getContext().getHost(), event.getContext().getPort(),
                                       this.maxConnectionsPerHost);
            this.contextMap.put(id, context);
        }

        context.addToQueue(event.getContext());
        this.drainQueueAndProcessResult(context);
    }

    protected void handleRequestComplete(RequestCompleteEvent event) {
        this.queuedRequests.decrementAndGet();

        HostContext context = this.contextMap.get(this.hostId(event.getContext()));
        if (context == null) {
            // Can only happen if context is cleaned meanwhile... ignore and bail out.
            return;
        }

        this.drainQueueAndProcessResult(context);
    }

    protected void handleConnectionOpen(ConnectionOpenEvent event) {
        String id = this.hostId(event.getConnection());
        HostContext context = this.contextMap.get(id);
        if (context == null) {
            throw new IllegalStateException("Context for id '" + id +
                                            "' does not exist (it may have been incorrectly cleaned up)");
        }

        context.getConnectionPool().connectionOpen(event.getConnection());
        // Rather than go through the whole process of drainQueue(), simplyp poll a single element from the head of
        // the queue into this connection (a newly opened connection is ALWAYS available).
        HttpRequestContext nextRequest = context.pollQueue();
        if (nextRequest != null) {
            event.getConnection().execute(nextRequest);
        }
    }

    protected void handleConnectionClosed(ConnectionClosedEvent event) {
        // Update the list of available connections for the same host:port.
        String id = this.hostId(event.getConnection());
        HostContext context = this.contextMap.get(id);
        if (context == null) {
            throw new IllegalStateException("Context for id '" + id +
                                            "' does not exist (it may have been incorrectly cleaned up)");
        }

        context.getConnectionPool().connectionClosed(event.getConnection());
        if ((context.getConnectionPool().getTotalConnections() == 0) && context.getQueue().isEmpty() &&
            this.cleanupInactiveHostContexts) {
            // No requests in queue, no connections open or opening... Cleanup resources.
            this.contextMap.remove(id);
        }
        this.drainQueueAndProcessResult(context);
    }

    protected void handleConnectionFailed(ConnectionFailedEvent event) {
        // Update the list of available connections for the same host:port.
        String id = this.hostId(event.getConnection());
        HostContext context = this.contextMap.get(id);
        if (context == null) {
            throw new IllegalStateException("Context for id '" + id +
                                            "' does not exist (it may have been incorrectly cleaned up)");
        }

        context.getConnectionPool().connectionFailed();
        if ((context.getConnectionPool().hasConnectionFailures() &&
             context.getConnectionPool().getTotalConnections() == 0)) {
            // Connection failures occured and there are no more connections active or establishing, so its time to
            // fail all queued requests.
            context.failAllRequests(HttpRequestFuture.CANNOT_CONNECT);
        }
    }

    protected void drainQueueAndProcessResult(HostContext context) {
        HostContext.DrainQueueResult result = context.drainQueue();
        switch (result) {
            case OPEN_CONNECTION:
                this.openConnection(context);
                break;
            case QUEUE_EMPTY:
            case NOT_DRAINED:
            case DRAINED:
            default:
        }
    }

    protected String hostId(HttpConnection connection) {
        return this.hostId(connection.getHost(), connection.getPort());
    }

    protected String hostId(HttpRequestContext context) {
        return this.hostId(context.getHost(), context.getPort());
    }

    protected String hostId(HostContext context) {
        return this.hostId(context.getHost(), context.getPort());
    }

    protected String hostId(String host, int port) {
        return new StringBuilder().append(host).append(":").append(port).toString();
    }

    protected void openConnection(final HostContext context) {
        // No need to recheck whether a connection can be opened or not, that was done already inside the HttpContext.

        // Try to create a pipeline before signalling a new connection is being open.
        // This should never throw exceptions but who knows...
        final ChannelPipeline pipeline;
        try {
            pipeline = this.pipelineFactory.getPipeline();
        } catch (Exception e) {
            LOG.error("Failed to create pipeline.", e);
            // bail out before marking a connection as opening.
            return;
        }

        // Signal that a new connection is opening.
        context.getConnectionPool().connectionOpening();

        // server:port-X
        String id = new StringBuilder().append(this.hostId(context)).append("-")
                .append(this.connectionCounter++).toString();

        // If not using NIO, then delegate the blocking write() call to the executor.
        Executor writeDelegator = this.useNio ? null : this.executor;

        final HttpConnection connection = this.connectionFactory
                .createConnection(id, context.getHost(), context.getPort(), this, this.timeoutManager, writeDelegator);

        pipeline.addLast("handler", connection);

        // Delegate actual connection to other thread, since calling connect is a blocking call.
        this.executor.execute(new Runnable() {
            @Override
            public void run() {
                ClientBootstrap bootstrap = new ClientBootstrap(channelFactory);
                bootstrap.setOption("reuseAddress", true);
                bootstrap.setOption("connectTimeoutMillis", connectionTimeoutInMillis);
                bootstrap.setPipeline(pipeline);
                bootstrap.connect(new InetSocketAddress(context.getHost(), context.getPort()))
                        .addListener(new ChannelFutureListener() {
                            @Override
                            public void operationComplete(ChannelFuture future) throws Exception {
                                if (future.isSuccess()) {
                                    channelGroup.add(future.getChannel());
                                }
                            }
                        });
            }
        });
    }

    // getters & setters ----------------------------------------------------------------------------------------------

    public boolean isUseSsl() {
        return useSsl;
    }

    /**
     * Whether this client should create SSL or non-SSL connections.
     * <p/>
     * All connections are affected by this flag.
     * <p/>
     * Defaults to {@code false}.
     *
     * @param useSsl {@code true} if all connections will have SSL support, {@code false} otherwise.
     */
    public void setUseSsl(boolean useSsl) {
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.useSsl = useSsl;
    }

    public int getRequestCompressionLevel() {
        return requestCompressionLevel;
    }

    /**
     * Level of compression when sending requests.
     * <p/>
     * Defaults to 0.
     *
     * @param requestCompressionLevel Level of compression between 0 and 9; 0 = off and 9 = max.
     */
    public void setRequestCompressionLevel(int requestCompressionLevel) {
        if ((requestCompressionLevel < 0) || (requestCompressionLevel > 9)) {
            throw new IllegalArgumentException("RequestCompressionLevel must be in range [0;9] (0 = none, 9 = max)");
        }
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.requestCompressionLevel = requestCompressionLevel;
    }

    public boolean isAutoInflate() {
        return autoInflate;
    }

    /**
     * Whether responses should be auto inflated (decompressed) or not.
     * <p/>
     * Setting this flag to true will cause a 'Accept-Encoding' header with value 'gzip' to be added to the requests
     * submitted.
     * <p/>
     * Defaults to {@code true}.
     *
     * @param autoInflate {@code true} if the connections should automatically decompress gzip content, {@code false}
     *                    otherwise.
     */
    public void setAutoInflate(boolean autoInflate) {
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.autoInflate = autoInflate;
    }

    public int getRequestChunkSize() {
        return requestChunkSize;
    }

    /**
     * Maximum size for HTTP request chunks.
     * If the contents of the requests exceed this value, the request will be chunked and a 'Transfer-Encoding' header
     * will be added with value 'chunked'.
     * <p/>
     * Defaults to 8192.
     *
     * @param requestChunkSize If request or response body exceeds this value
     */
    public void setRequestChunkSize(int requestChunkSize) {
        if (requestChunkSize < 128) {
            throw new IllegalArgumentException("Minimum accepted chunk size is 128b");
        }
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.requestChunkSize = requestChunkSize;
    }

    public boolean isAggregateResponseChunks() {
        return aggregateResponseChunks;
    }

    /**
     * If the response is transferred in chunks, whether they should be automatically grouped or not.
     * <p/>
     * Defaults to {@code true}.
     *
     * @param aggregateResponseChunks {@code true} to aggregate http response chunks automatically, {@code false}
     *                                otherwise.
     */
    public void setAggregateResponseChunks(boolean aggregateResponseChunks) {
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.aggregateResponseChunks = aggregateResponseChunks;
    }

    public int getMaxConnectionsPerHost() {
        return maxConnectionsPerHost;
    }

    /**
     * Sets the maximum number of active connections per host.
     * <p/>
     * This number also limits the number of connections being established so that
     * {@code connectionsOpen + connectionsOpening <= maxConnectionsPerHost} is always true.
     * <p/>
     * Defaults to 3.
     *
     * @param maxConnectionsPerHost Maximum number of total active connections (open + opening) per host at a given
     *                              time. Minimum value is 1.
     */
    public void setMaxConnectionsPerHost(int maxConnectionsPerHost) {
        if (maxConnectionsPerHost < 1) {
            throw new IllegalArgumentException("MaxConnectionsPerHost must be > 1");
        }
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.maxConnectionsPerHost = maxConnectionsPerHost;
    }

    public int getMaxQueuedRequests() {
        return this.maxQueuedRequests;
    }

    /**
     * Sets the maximum number of queued requests for this client.
     * <p/>
     * If the number of queued requests is exceeded, calling
     * {@linkplain #execute(String, int, HttpRequest, HttpResponseProcessor) one of the variants of {@code execute()}}
     * will throw a {@link CannotExecuteRequestException}.
     * <p/>
     * Defaults to {@link Short#MAX_VALUE}.
     *
     * @param maxQueuedRequests Maximum number of queued requests at any given moment.
     */
    public void setMaxQueuedRequests(int maxQueuedRequests) {
        if (maxQueuedRequests < 1) {
            throw new IllegalArgumentException("MaxQueuedRequests must be > 1");
        }
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.maxQueuedRequests = maxQueuedRequests;
    }

    public int getConnectionTimeoutInMillis() {
        return connectionTimeoutInMillis;
    }

    /**
     * Sets the connection to host timeout, in milliseconds.
     * <p/>
     * Defaults to 2000.
     *
     * @param connectionTimeoutInMillis Connection to host timeout, in milliseconds.
     */
    public void setConnectionTimeoutInMillis(int connectionTimeoutInMillis) {
        if (connectionTimeoutInMillis <= 0) {
            throw new IllegalArgumentException("ConnectionTimeoutInMillis must be >= 0 (0 means infinite)");
        }
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.connectionTimeoutInMillis = connectionTimeoutInMillis;
    }

    public int getRequestTimeoutInMillis() {
        return requestTimeoutInMillis;
    }

    /**
     * Sets the default request timeout, in milliseconds.
     * <p/>
     * When {@link #execute(String, int, HttpRequest, HttpResponseProcessor)} is called (i.e. the variant without
     * explicit request timeout) then this value is applied as the request timeout.
     * <p/>
     * Requests whose execution time exceeds (precision depends on the {@link TimeoutManager} chosen) this value will be
     * considered failed and their {@link HttpRequestFuture} will be released with cause
     * {@link HttpRequestFuture#TIMED_OUT}.
     * <p/>
     * Defaults to 2000.
     *
     * @param requestTimeoutInMillis Default request timeout, in milliseconds.
     */
    public void setRequestTimeoutInMillis(int requestTimeoutInMillis) {
        if (requestTimeoutInMillis <= 0) {
            throw new IllegalArgumentException("RequestTimeoutInMillis must be >= 0 (0 means infinite)");
        }
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.requestTimeoutInMillis = requestTimeoutInMillis;
    }

    public boolean isUseNio() {
        return useNio;
    }

    /**
     * Whether this client should use non-blocking IO (New I/O or NIO) or blocking IO (Plain Socket Old IO or OIO).
     * <p/>
     * NIO is generally better for higher throughput (scenarios with an elevated number of open connections) while OIO
     * is always better for latency (and scenarios where a low number of connections is open).
     * <p/>
     * If the number of connections open is not supposed to exceed 10~20, then use OIO as it typically presents better
     * results.
     * <p/>
     * Since the writes in OIO are blocking, the HTTP connections will delegate the call to
     * {@link org.jboss.netty.channel.Channel#write(Object)} to an executor (provided by this {@link HttpClient}).
     * <p/>
     * Defaults to {@code true}.
     *
     * @param useNio {@code true} if this client should use NIO, {@code false} if it should use OIO.
     */
    public void setUseNio(boolean useNio) {
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.useNio = useNio;
    }

    public int getMaxIoWorkerThreads() {
        return maxIoWorkerThreads;
    }

    /**
     * Maximum number of worker threads for the executor provided to Netty's {@link ChannelFactory}.
     * <p/>
     * Defaults to 50.
     *
     * @param maxIoWorkerThreads Maximum number of IO worker threads.
     */
    public void setMaxIoWorkerThreads(int maxIoWorkerThreads) {
        if (maxIoWorkerThreads <= 1) {
            throw new IllegalArgumentException("Minimum value for maxIoWorkerThreads is 1");
        }
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.maxIoWorkerThreads = maxIoWorkerThreads;
    }

    public int getMaxEventProcessorHelperThreads() {
        return maxEventProcessorHelperThreads;
    }

    /**
     * Maximum number of helper threads for the event processor.
     * <p/>
     * There are tasks performed by the internal event processor that are blocking and/or slow and need not be executed
     * in serial mode. Therefore the event processor delegates them to helper threads in order to keep doing what it's
     * supposed to do: consume events from the event queue.
     * <p/>
     * Defaults to 20.
     *
     * @param maxEventProcessorHelperThreads Maximum number of IO worker threads.
     */
    public void setMaxEventProcessorHelperThreads(int maxEventProcessorHelperThreads) {
        if (maxEventProcessorHelperThreads <= 3) {
            throw new IllegalArgumentException("Minimum value for maxEventProcessorHelperThreads is 3");
        }
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.maxEventProcessorHelperThreads = maxEventProcessorHelperThreads;
    }

    public HostContextFactory getHostContextFactory() {
        return hostContextFactory;
    }

    /**
     * The {@link HostContextFactory} that will be used to create new {@link HostContext} instances.
     * <p/>
     * Defaults to {@link DefaultHostContextFactory} if none is provided.
     *
     * @param hostContextFactory The {@link HostContextFactory} to be used.
     *
     * @see org.factor45.hotpotato.client.host.factory.HostContextFactory
     * @see org.factor45.hotpotato.client.host.HostContext
     */
    public void setHostContextFactory(HostContextFactory hostContextFactory) {
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.hostContextFactory = hostContextFactory;
    }

    public HttpConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * The {@link HttpConnectionFactory} that will be used to create new {@link HttpConnection}.
     * <p/>
     * Defaults to {@link DefaultHttpConnectionFactory} if none is provided.
     *
     * @param connectionFactory The {@link HttpConnectionFactory} to be used.
     *
     * @see org.factor45.hotpotato.client.connection.factory.HttpConnectionFactory
     * @see org.factor45.hotpotato.client.connection.HttpConnection
     */
    public void setConnectionFactory(HttpConnectionFactory connectionFactory) {
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.connectionFactory = connectionFactory;
    }

    public HttpRequestFutureFactory getFutureFactory() {
        return futureFactory;
    }

    /**
     * The {@link HttpRequestFutureFactory} that will be used to create new {@link HttpRequestFuture}.
     * <p/>
     * Defaults to {@link DefaultHttpRequestFutureFactory} if none is provided.
     *
     * @param futureFactory The {@link HttpRequestFutureFactory} to be used.
     *
     * @see org.factor45.hotpotato.request.factory.HttpRequestFutureFactory
     * @see org.factor45.hotpotato.request.HttpRequestFuture
     */
    public void setFutureFactory(HttpRequestFutureFactory futureFactory) {
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }
        this.futureFactory = futureFactory;
    }

    public TimeoutManager getTimeoutManager() {
        return timeoutManager;
    }

    /**
     * The {@link TimeoutManager} that will be used to check request timeouts.
     * <p/>
     * If no instance is provided, a new instance is created upon calling {@link #init()}. This instance will be
     * automatically terminated when {@link #terminate()} is called.
     * <p/>
     * If an external {@link TimeoutManager} is provided, then it must be pre-initialised (i.e. its
     * {@link TimeoutManager#init()} must be called and return {@code true}) and it must be post-terminated (i.e. its
     * {@link TimeoutManager#terminate()} must be called after this instance of {@link HttpClient} is disposed).
     * <p/>
     * Defaults to a new instance of {@link HashedWheelTimeoutManager}.
     *
     * @param timeoutManager The {@link TimeoutManager} instance to use.
     *
     * @see org.factor45.hotpotato.client.timeout.TimeoutManager
     */
    public void setTimeoutManager(TimeoutManager timeoutManager) {
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }

        this.timeoutManager = timeoutManager;
    }

    public boolean isCleanupInactiveHostContexts() {
        return cleanupInactiveHostContexts;
    }

    /**
     * Whether empty {@link HostContext}s should be immediately cleaned up.
     * <p/>
     * When a {@linkplain HostContext host context} has no more queued requests nor active connections nor connections
     * opening, it is eligible for cleanup. Setting this flag to {@code true} will cause them to be instantly reaped
     * when such conditions are met.
     * <p/>
     * Unless your client will be performing requests to many different host/port combinations, you should set this flag
     * to {@code false}. While the overhead of creating/cleaning these contexts is minimal, it can be avoided in these
     * scenarios.
     * <p/>
     * Defaults to {@code true}.
     *
     * @param cleanupInactiveHostContexts {@code true} if inactive host contexts should be cleaned up, {@code false}
     *                                    otherwise.
     *
     * @see org.factor45.hotpotato.client.host.HostContext
     */
    public void setCleanupInactiveHostContexts(boolean cleanupInactiveHostContexts) {
        if (this.eventQueue != null) {
            throw new IllegalStateException("Cannot modify property after initialisation");
        }

        this.cleanupInactiveHostContexts = cleanupInactiveHostContexts;
    }

    public Map<String, HostContext> getContextMap() {
        // Purely for unit testing purposes...
        return Collections.unmodifiableMap(this.contextMap);
    }

    // low level overrides --------------------------------------------------------------------------------------------


    @Override
    public String toString() {
        return this.getClass().getSimpleName() + '@' + Integer.toHexString(this.hashCode());
    }
}
