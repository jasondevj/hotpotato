package org.factor45.hotpotato.client.host;

import org.factor45.hotpotato.client.HostContextTestUtil;
import org.factor45.hotpotato.client.HttpConnectionTestUtil;
import org.factor45.hotpotato.client.HttpRequestContext;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class EagerDrainHostContextTest {

    private AbstractHostContext hostContext;

    @Before
    public void setUp() {
        String host = "localhost";
        int port = 80;
        this.hostContext = new EagerDrainHostContext(host, port, 2);
        for (int i = 0; i < 4; i++) {
            HttpRequestContext<Object> requestContext = HostContextTestUtil.generateDummyContext(host, port);
            this.hostContext.addToQueue(requestContext);
        }
    }

    @Test
    public void testDrainQueueWithAvailableConnection() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().getTotalConnections());
        this.hostContext.getConnectionPool().connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection());
        this.hostContext.getConnectionPool().connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection());
        assertEquals(2, this.hostContext.getConnectionPool().getTotalConnections());
        assertEquals(4, this.hostContext.getQueue().size());

        assertEquals(HostContext.DrainQueueResult.DRAINED, this.hostContext.drainQueue());
        assertEquals(2, this.hostContext.getQueue().size());
    }
}
