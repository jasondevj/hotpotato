package org.factor45.hotpotato.client.timeout;

import org.factor45.hotpotato.client.HostContextTestUtil;
import org.factor45.hotpotato.client.HttpRequestContext;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class TimeoutManagerTest {

    @Test
    public void testHashedWheelRequestTimeout() throws Exception {
        TimeoutManager manager = new HashedWheelTimeoutManager(100, TimeUnit.MILLISECONDS, 512);
        manager.init();
        HttpRequestContext context = HostContextTestUtil.generateDummyContext("localhost", 8080, 500);
        manager.manageRequestTimeout(context);

        Thread.sleep(1000L);

        assertTrue(context.getFuture().isDone());
        assertFalse(context.getFuture().isSuccess());
        assertNotNull(context.getFuture().getCause());
        assertEquals(HttpRequestFuture.TIMED_OUT, context.getFuture().getCause());
        System.out.println(context.getFuture().getExistenceTime());
        manager.terminate();
    }

    @Test
    public void testBasicRequestTimeout() throws Exception {
        TimeoutManager manager = new BasicTimeoutManager(1);
        manager.init();
        HttpRequestContext context = HostContextTestUtil.generateDummyContext("localhost", 8080, 500);
        manager.manageRequestTimeout(context);

        Thread.sleep(1000L);

        assertTrue(context.getFuture().isDone());
        assertFalse(context.getFuture().isSuccess());
        assertNotNull(context.getFuture().getCause());
        assertEquals(HttpRequestFuture.TIMED_OUT, context.getFuture().getCause());
        System.out.println(context.getFuture().getExistenceTime());
        manager.terminate();
    }
}
