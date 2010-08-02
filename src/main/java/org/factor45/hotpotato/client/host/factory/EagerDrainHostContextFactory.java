package org.factor45.hotpotato.client.host.factory;

import org.factor45.hotpotato.client.host.EagerDrainHostContext;
import org.factor45.hotpotato.client.host.HostContext;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class EagerDrainHostContextFactory implements HostContextFactory {

    // HostContextFactory ---------------------------------------------------------------------------------------------

    @Override
    public HostContext createHostContext(String host, int port, int maxConnections) {
        return new EagerDrainHostContext(host, port, maxConnections);
    }
}
