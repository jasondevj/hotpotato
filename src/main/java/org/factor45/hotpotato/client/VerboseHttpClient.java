package org.factor45.hotpotato.client;

import org.factor45.hotpotato.client.connection.factory.DefaultHttpConnectionFactory;
import org.factor45.hotpotato.client.event.ConnectionClosedEvent;
import org.factor45.hotpotato.client.event.ConnectionFailedEvent;
import org.factor45.hotpotato.client.event.ConnectionOpenEvent;
import org.factor45.hotpotato.client.event.EventType;
import org.factor45.hotpotato.client.event.ExecuteRequestEvent;
import org.factor45.hotpotato.client.event.HttpClientEvent;
import org.factor45.hotpotato.client.event.RequestCompleteEvent;
import org.factor45.hotpotato.client.host.factory.DefaultHostContextFactory;
import org.factor45.hotpotato.client.host.HostContext;
import org.factor45.hotpotato.client.timeout.HashedWheelTimeoutManager;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.channel.socket.oio.OioClientSocketChannelFactory;
import org.jboss.netty.example.securechat.SecureChatSslContextFactory;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpContentDecompressor;
import org.jboss.netty.handler.ssl.SslHandler;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class VerboseHttpClient extends AbstractHttpClient implements EventProcessorStatsProvider {

    // internal vars --------------------------------------------------------------------------------------------------

    protected long totalTime = 0;
    protected long executeRequestTime = 0;
    protected long requestCompleteTime = 0;
    protected long connectionOpenTime = 0;
    protected long connectionClosedTime = 0;
    protected long connectionFailedTime = 0;
    protected int events = 0;

    @Override
    public boolean init() {
        if (this.timeoutManager == null) {
            // Consumes less resources, puts less emphasis on precision.
            this.timeoutManager = new HashedWheelTimeoutManager();
            //this.timeoutManager = new BasicTimeoutManager(10);
            this.timeoutManager.init();
            this.internalTimeoutManager = true;
        }

        this.eventQueue = new LinkedBlockingQueue<HttpClientEvent>();
        if (this.hostContextFactory == null) {
            this.hostContextFactory = new DefaultHostContextFactory();
        }
        if (this.connectionFactory == null) {
            this.connectionFactory = new DefaultHttpConnectionFactory();
        }
        this.eventConsumerLatch = new CountDownLatch(1);

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
    protected void eventHandlingLoop() {
        for (;;) {
            // Manual synchronisation here because before removing an element, we first need to check whether an
            // active available connection exists to satisfy the request.
            try {
                System.err.println("---------------------------------------------------------------");
                HttpClientEvent event = eventQueue.take();
                if (event == POISON) {
                    this.eventConsumerLatch.countDown();
                    return;
                }
                this.events++;
                long start = System.nanoTime();

                System.err.println("[EHL] Handling event: " + event);
                System.err.println("[EHL] Event queue ---");
                int i = 0;
                for (HttpClientEvent e : this.eventQueue) {
                    System.err.println("      " + (++i) + ". " + e);
                }
                System.err.println("[EHL] ---------------\n");

                switch (event.getEventType()) {
                    case EXECUTE_REQUEST:
                        this.handleExecuteRequest((ExecuteRequestEvent) event);
                        this.executeRequestTime += System.nanoTime() - start;
                        break;
                    case REQUEST_COMPLETE:
                        this.handleRequestComplete((RequestCompleteEvent) event);
                        this.requestCompleteTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_OPEN:
                        this.handleConnectionOpen((ConnectionOpenEvent) event);
                        this.connectionOpenTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_CLOSED:
                        this.handleConnectionClosed((ConnectionClosedEvent) event);
                        this.connectionClosedTime += System.nanoTime() - start;
                        break;
                    case CONNECTION_FAILED:
                        this.handleConnectionFailed((ConnectionFailedEvent) event);
                        this.connectionFailedTime += System.nanoTime() - start;
                        break;
                    default:
                        // Consume and do nothing, unknown event.
                }
                this.totalTime += System.nanoTime() - start;
            } catch (InterruptedException e) {
                // ignore, poisoning the queue is the only way to stop
            }
        }
    }

    @Override
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
            System.err.println("[EHL-hCF] Last of connection attempts for " + id + " failed; " +
                               "cancelling all queued requests.");
            // Connection failures occured and there are no more connections active or establishing, so its time to
            // fail all queued requests.
            context.failAllRequests(HttpRequestFuture.CANNOT_CONNECT);
        }
    }

    @Override
    protected void drainQueueAndProcessResult(HostContext context) {
        HostContext.DrainQueueResult result = context.drainQueue();
        System.err.println("[EHL-dQAPR] drainQueue() result was " + result);
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

    @Override
    protected void openConnection(HostContext context) {
        System.err.println("[EHL-OC] Opening connection to " + this.hostId(context));
        super.openConnection(context);
    }

    // EventProcessorStatsProvider ------------------------------------------------------------------------------------

    @Override
    public long getTotalExecutionTime() {
        return this.totalTime / 1000000;
    }

    @Override
    public long getEventProcessingTime(EventType event) {
        switch (event) {
            case EXECUTE_REQUEST:
                return this.executeRequestTime / 1000000;
            case REQUEST_COMPLETE:
                return this.requestCompleteTime / 1000000;
            case CONNECTION_OPEN:
                return this.connectionOpenTime / 1000000;
            case CONNECTION_CLOSED:
                return this.connectionClosedTime / 1000000;
            case CONNECTION_FAILED:
                return this.connectionFailedTime / 1000000;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }

    @Override
    public float getEventProcessingPercentage(EventType event) {
        switch (event) {
            case EXECUTE_REQUEST:
                return (this.executeRequestTime / (float) this.totalTime) * 100;
            case REQUEST_COMPLETE:
                return (this.requestCompleteTime / (float) this.totalTime) * 100;
            case CONNECTION_OPEN:
                return (this.connectionOpenTime / (float) this.totalTime) * 100;
            case CONNECTION_CLOSED:
                return (this.connectionClosedTime / (float) this.totalTime) * 100;
            case CONNECTION_FAILED:
                return (this.connectionFailedTime / (float) this.totalTime) * 100;
            default:
                throw new IllegalArgumentException("Unsupported event type: " + event);
        }
    }

    @Override
    public long getProcessedEvents() {
        return this.events;
    }
}
