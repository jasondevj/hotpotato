package com.biasedbit.hotpotato.client.host;

import com.biasedbit.hotpotato.client.HostContextTestUtil;
import com.biasedbit.hotpotato.client.HttpConnectionTestUtil;
import com.biasedbit.hotpotato.client.HttpRequestContext;
import com.biasedbit.hotpotato.request.HttpRequestFuture;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class DefaultHostContextTest {

    private AbstractHostContext hostContext;
    private List<HttpRequestContext<Object>> requestContexts;

    @Before
    public void setUp() {
        String host = "localhost";
        int port = 80;
        this.hostContext = new DefaultHostContext(host, port, 2);
        this.requestContexts = new ArrayList<HttpRequestContext<Object>>(4);
        for (int i = 0; i < 4; i++) {
            HttpRequestContext<Object> requestContext = HostContextTestUtil.generateDummyContext(host, port);
            this.requestContexts.add(requestContext);
            this.hostContext.addToQueue(requestContext);
        }
    }
    
    @Test
    public void testDrainQueueWithAvailableConnection() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().getTotalConnections());
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection("id", "host", 0, null));
        assertEquals(1, this.hostContext.getConnectionPool().getTotalConnections());
        assertEquals(4, this.hostContext.getQueue().size());

        assertEquals(HostContext.DrainQueueResult.DRAINED, this.hostContext.drainQueue());
        assertEquals(3, this.hostContext.getQueue().size());
    }

    @Test
    public void testDrainQueueWithNoConnection() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().getTotalConnections());
        assertEquals(4, this.hostContext.getQueue().size());

        assertEquals(HostContext.DrainQueueResult.OPEN_CONNECTION, this.hostContext.drainQueue());
        assertEquals(4, this.hostContext.getQueue().size());
    }

    @Test
    public void testDrainQueueWithAllConnectionsExausted() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().getTotalConnections());
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.NeverAvailableHttpConnection("id", "host", 0, null));
        this.hostContext.getConnectionPool().connectionOpening();
        assertEquals(2, this.hostContext.getConnectionPool().getTotalConnections());
        assertEquals(4, this.hostContext.getQueue().size());

        assertEquals(HostContext.DrainQueueResult.NOT_DRAINED, this.hostContext.drainQueue());
        assertEquals(4, this.hostContext.getQueue().size());
    }

    @Test
    public void testDrainQueueWithQueueEmpty() throws Exception {
        assertNotNull(this.hostContext.getConnectionPool());
        assertEquals(0, this.hostContext.getConnectionPool().getTotalConnections());
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection("id", "host", 0, null));
        this.hostContext.getConnectionPool()
                .connectionOpen(new HttpConnectionTestUtil.AlwaysAvailableHttpConnection("id", "host", 0, null));
        assertEquals(2, this.hostContext.getConnectionPool().getTotalConnections());
        this.hostContext.drainQueue();
        this.hostContext.drainQueue();
        this.hostContext.drainQueue();
        this.hostContext.drainQueue();
        assertEquals(0, this.hostContext.getQueue().size());
        assertEquals(HostContext.DrainQueueResult.QUEUE_EMPTY, this.hostContext.drainQueue());
        assertEquals(0, this.hostContext.getQueue().size());
    }

    @Test
    public void testFailAllRequests() throws Exception {
        this.hostContext.failAllRequests(HttpRequestFuture.CONNECTION_LOST);
        for (HttpRequestContext<Object> request : this.requestContexts) {
            assertFalse(request.getFuture().isSuccess());
            assertEquals(HttpRequestFuture.CONNECTION_LOST, request.getFuture().getCause());
        }
    }
}
