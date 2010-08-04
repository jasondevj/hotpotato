package org.factor45.hotpotato.client.connection.factory;

import org.factor45.hotpotato.client.connection.HttpConnection;
import org.factor45.hotpotato.client.connection.HttpConnectionListener;
import org.factor45.hotpotato.client.timeout.TimeoutManager;

import java.util.concurrent.Executor;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public interface HttpConnectionFactory {

    HttpConnection getConnection(String id, String host, int port, HttpConnectionListener listener,
                                 TimeoutManager manager);

    HttpConnection getConnection(String id, String host, int port, HttpConnectionListener listener,
                                 TimeoutManager manager, Executor executor);
}
