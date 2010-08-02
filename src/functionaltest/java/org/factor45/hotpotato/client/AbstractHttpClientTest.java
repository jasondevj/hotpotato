package org.factor45.hotpotato.client;

import org.factor45.hotpotato.client.factory.DefaultHttpClientFactory;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.response.DiscardProcessor;
import org.factor45.hotpotato.utils.DummyHttpServer;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:bruno.carvalho@wit-software.com">Bruno de Carvalho</a>
 */
public class AbstractHttpClientTest {

    @Test
    public void testCancellationOnAllPendingRequests() throws Exception {
        // This test tests that *ALL* futures are unlocked when a premature shutdown is called on the client, no matter
        // where they are:
        //   1) the event queue (pre-processing)
        //   2) the request-queue (processed and queued)
        //   3) inside the connection (executing).
        // Ensuring all futures are unlocked under all circumstances is vital to keep this library from generating code
        // stalls.

        DummyHttpServer server = new DummyHttpServer("localhost", 8080);
        server.setFailureProbability(0.0f);
        server.setResponseLatency(50L);
        assertTrue(server.init());

        DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
        //factory.setDebug(true);
        final HttpClient client = factory.getClient();
        assertTrue(client.init());

        List<HttpRequestFuture<Object>> futures = new ArrayList<HttpRequestFuture<Object>>();
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        for (int i = 0; i < 1000; i++) {
            futures.add(client.execute("localhost", 8080, request, new DiscardProcessor()));
        }

        // server is configured to sleep for 50ms in each request so only the first 3 should complete.
        Thread.sleep(150L);
        client.terminate();

        long complete = 0;
        for (HttpRequestFuture<Object> future : futures) {
            assertTrue(future.isDone());
            if (future.isSuccess()) {
                complete++;
            } else {
                assertEquals(HttpRequestFuture.SHUTTING_DOWN, future.getCause());
            }
        }

        // Should print 3 or 6/1000... Really depends on the computer though...
        System.out.println(complete + "/1000 requests were executed. All others failed with cause: " +
                           HttpRequestFuture.SHUTTING_DOWN);
    }

    @Test
    public void testRequestTimeout() {
        DummyHttpServer server = new DummyHttpServer("localhost", 8080);
        server.setFailureProbability(0.0f);
        server.setResponseLatency(50L);
        assertTrue(server.init());

        DefaultHttpClientFactory factory = new DefaultHttpClientFactory();
        //factory.setDebug(true);
        final HttpClient client = factory.getClient();
        assertTrue(client.init());

        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        HttpRequestFuture<Object> future = client.execute("localhost", 8080, 40, request, new DiscardProcessor());
        assertTrue(future.awaitUninterruptibly(1000L));
        assertTrue(future.isDone());
        assertFalse(future.isSuccess());
        assertEquals(HttpRequestFuture.TIMED_OUT, future.getCause());
        client.terminate();
    }
}
