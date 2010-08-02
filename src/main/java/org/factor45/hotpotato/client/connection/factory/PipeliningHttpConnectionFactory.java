package org.factor45.hotpotato.client.connection.factory;

import org.factor45.hotpotato.client.connection.HttpConnection;
import org.factor45.hotpotato.client.connection.HttpConnectionListener;
import org.factor45.hotpotato.client.connection.PipeliningHttpConnection;

import java.util.concurrent.Executor;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
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
    public HttpConnection getConnection(String id, String host, int port, HttpConnectionListener listener,
                                        Executor executor, boolean delegateWritesToExecutor) {
        PipeliningHttpConnection connection = new PipeliningHttpConnection(id, host, port, listener,
                                                                           delegateWritesToExecutor);
        connection.setAllowPostPipelining(this.allowPostPipelining);
        connection.setDisconnectIfNonKeepAliveRequest(this.disconnectIfNonKeepAliveRequest);
        return connection;
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
