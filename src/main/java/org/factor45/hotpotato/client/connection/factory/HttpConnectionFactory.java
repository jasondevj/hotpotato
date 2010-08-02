package org.factor45.hotpotato.client.connection.factory;

import org.factor45.hotpotato.client.connection.HttpConnection;
import org.factor45.hotpotato.client.connection.HttpConnectionListener;

import java.util.concurrent.Executor;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public interface HttpConnectionFactory {

    HttpConnection getConnection(String id, String host, int port, HttpConnectionListener listener, Executor executor,
                                 boolean delegateWritesToExecutor);
}
