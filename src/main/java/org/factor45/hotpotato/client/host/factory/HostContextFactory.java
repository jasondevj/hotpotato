package org.factor45.hotpotato.client.host.factory;

import org.factor45.hotpotato.client.host.HostContext;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public interface HostContextFactory {

    HostContext createHostContext(String host, int port, int maxConnections);
}
