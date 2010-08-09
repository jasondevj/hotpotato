package org.factor45.hotpotato.client.host;

import org.factor45.hotpotato.client.ConnectionPool;
import org.factor45.hotpotato.client.HttpRequestContext;

import java.util.Queue;

/**
 * HostContexts store context on a per-host basis.
 * It serves as a helper component to help keep HttpClient implementations cleaner.
 *
 * Also, it is up to implementations of this interface to determine exactly how they drain the queue.
 * By returning different values, they can influence how the HttpClient to behaves (e.g.: they can request a new
 * connection, they can drain 1 element, they can drain multiple elements, etc).
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public interface HostContext {

    public enum DrainQueueResult {
        QUEUE_EMPTY,
        DRAINED,
        NOT_DRAINED,
        OPEN_CONNECTION,
    }

    String getHost();

    int getPort();

    ConnectionPool getConnectionPool();

    Queue<HttpRequestContext> getQueue();

    void addToQueue(HttpRequestContext request);

    DrainQueueResult drainQueue();

    HttpRequestContext pollQueue();
    
    void failAllRequests(Throwable cause);

    long lastActivity();
}
