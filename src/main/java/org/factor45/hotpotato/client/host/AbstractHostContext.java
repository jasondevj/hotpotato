package org.factor45.hotpotato.client.host;

import org.factor45.hotpotato.client.ConnectionPool;
import org.factor45.hotpotato.client.HttpRequestContext;
import org.factor45.hotpotato.client.connection.HttpConnection;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Abstract implementation of the {@link HostContext} interface.
 *
 * This class contains boilerplate code that all implementations of {@link HostContext} would surely have as well.
 * The important logic is present in {@link #drainQueue()}, method that needs to be implemented by extensions of this
 * class.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public abstract class AbstractHostContext implements HostContext {

    // internal vars --------------------------------------------------------------------------------------------------

    protected final String host;
    protected final int port;
    protected final int maxConnections;
    protected final ConnectionPool connectionPool;
    protected final Queue<HttpRequestContext> queue;
    protected long lastActivity;

    // constructors ---------------------------------------------------------------------------------------------------

    public AbstractHostContext(String host, int port, int maxConnections) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("MaxConnections must be > 0");
        }

        this.host = host;
        this.port = port;
        this.maxConnections = maxConnections;
        this.connectionPool = new ConnectionPool();
        this.queue = new LinkedList<HttpRequestContext>();
        this.lastActivity = System.currentTimeMillis();
    }

    // HostContext ----------------------------------------------------------------------------------------------------

    @Override
    public String getHost() {
        return this.host;
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public ConnectionPool getConnectionPool() {
        return this.connectionPool;
    }

    @Override
    public Queue<HttpRequestContext> getQueue() {
        return this.queue;
    }

    @Override
    public void addToQueue(HttpRequestContext request) {
        this.lastActivity = System.currentTimeMillis();
        this.queue.add(request);
    }

    @Override
    public DrainQueueResult drainQueue() {
        this.lastActivity = System.currentTimeMillis();
        // 1. Test if there's anything to drain
        if (this.queue.isEmpty()) {
            return DrainQueueResult.QUEUE_EMPTY;
        }

        // 2. There are contents to drain, test if there are any connections created.
        if (this.connectionPool.getConnections().isEmpty()) {
            // 2a. No connections open, test if there is still room to create a new one.
            if (this.connectionPool.getTotalConnections() < this.maxConnections) {
                return DrainQueueResult.OPEN_CONNECTION;
            } else {
                return DrainQueueResult.NOT_DRAINED;
            }
        }

        // 3. There is content to drain and there are connections, iterate them to find an available one.
        for (HttpConnection connection : this.connectionPool.getConnections()) {
            if (connection.isAvailable()) {
                // Found an available connection; peek the first request and attempt to execute it.
                HttpRequestContext context = this.queue.peek();
                if (connection.execute(context)) {
                    // If the request was executed it means the connection wasn't terminating and it's still connected.
                    // Remove it from the queue (it was only previously peeked) and return DRAINED.
                    this.queue.remove();
                    return DrainQueueResult.DRAINED;
                }
            }
        }

        // 4. There were connections open but none of them was available; if possible, request a new one.
        if (this.connectionPool.getTotalConnections() < this.maxConnections) {
            return DrainQueueResult.OPEN_CONNECTION;
        } else {
            return DrainQueueResult.NOT_DRAINED;
        }
    }

    @Override
    public HttpRequestContext pollQueue() {
        return this.queue.poll();
    }

    @Override
    public void failAllRequests(Throwable cause) {
        for (HttpRequestContext context : this.queue) {
            context.getFuture().setFailure(cause);
        }
        System.err.println("cancelled " + this.queue.size() + " requests");
        this.queue.clear();
    }

    @Override
    public long lastActivity() {
        return this.lastActivity;
    }
}
