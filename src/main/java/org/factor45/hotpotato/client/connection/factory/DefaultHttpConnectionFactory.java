package org.factor45.hotpotato.client.connection.factory;

import org.factor45.hotpotato.client.connection.DefaultHttpConnection;
import org.factor45.hotpotato.client.connection.HttpConnection;
import org.factor45.hotpotato.client.connection.HttpConnectionListener;

import java.util.concurrent.Executor;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class DefaultHttpConnectionFactory implements HttpConnectionFactory {

    // configuration defaults -----------------------------------------------------------------------------------------

    private static final boolean DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST = false;

    // configuration --------------------------------------------------------------------------------------------------

    private boolean disconnectIfNonKeepAliveRequest;

    // constructors ---------------------------------------------------------------------------------------------------

    public DefaultHttpConnectionFactory() {
        this.disconnectIfNonKeepAliveRequest = DISCONNECT_IF_NON_KEEP_ALIVE_REQUEST;
    }

    // HttpConnectionFactory ------------------------------------------------------------------------------------------

    @Override
    public HttpConnection getConnection(String id, String host, int port, HttpConnectionListener listener,
                                        Executor executor, boolean delegateWritesToExecutor) {
        DefaultHttpConnection connection = new DefaultHttpConnection(id, host, port, listener, executor,
                                                                     delegateWritesToExecutor);
        connection.setDisconnectIfNonKeepAliveRequest(this.disconnectIfNonKeepAliveRequest);
        return connection;
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
     *         Whether the generated {@code HttpConnection}s should explicitly disconnect after executing a
     *         non-keepalive request.
     */
    public void setDisconnectIfNonKeepAliveRequest(boolean disconnectIfNonKeepAliveRequest) {
        this.disconnectIfNonKeepAliveRequest = disconnectIfNonKeepAliveRequest;
    }
}
