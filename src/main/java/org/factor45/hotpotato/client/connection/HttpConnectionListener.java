package org.factor45.hotpotato.client.connection;

import org.factor45.hotpotato.client.HttpRequestContext;

/**
 * {@link HttpConnection} listener.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public interface HttpConnectionListener {

    /**
     * Connection opened event, called by the {@link HttpConnection} when a requested connection establishes.
     *
     * @param connection Connection that just established.
     */
    void connectionOpened(HttpConnection connection);

    /**
     * Connection terminated event, called by the {@link HttpConnection} when an active connection disconnects.
     *
     * @param connection Connection that was disconnected.
     */
    void connectionTerminated(HttpConnection connection);

    /**
     * Connection failed event, called by the {@link HttpConnection} when a connection attempt fails.
     *
     * @param connection Connection that failed.
     */
    void connectionFailed(HttpConnection connection);

    /**
     * Request complete event, called by the {@link HttpConnection} when a response to a request allocated to it is
     * either received or fails for some reason.
     *
     * @param connection Connection in which the event finished.
     * @param context Request context containing the request that has completed.
     */
    void requestFinished(HttpConnection connection, HttpRequestContext context);
}
