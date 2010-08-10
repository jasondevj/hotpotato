package org.factor45.hotpotato.client.connection.factory;

import org.factor45.hotpotato.client.connection.HttpConnection;
import org.factor45.hotpotato.client.connection.HttpConnectionListener;
import org.factor45.hotpotato.client.timeout.TimeoutManager;
import org.jboss.netty.channel.group.ChannelGroup;

import java.util.concurrent.Executor;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public interface HttpConnectionFactory {

    HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                    TimeoutManager manager);

    HttpConnection createConnection(String id, String host, int port, HttpConnectionListener listener,
                                    TimeoutManager manager, Executor executor);
}
