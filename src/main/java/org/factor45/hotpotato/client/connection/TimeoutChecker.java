package org.factor45.hotpotato.client.connection;

import org.factor45.hotpotato.client.HttpRequestContext;
import org.factor45.hotpotato.request.HttpRequestFuture;
import org.factor45.hotpotato.request.HttpRequestFutureListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Checks if a given request times out before its execution completes.
 * <p/>
 * When the {@link #run()} method is called, the timeout checker binds itself as a listener to the provided {@link
 * HttpRequestContext}.
 * <p/>
 * The motive behind using a {@code TimeoutChecker} instead of having some internal thread in each connection that
 * shares state variables is that on some rare occasions, while executing runs with client and server on the same host,
 * the responseses are actually received faster than the timeout checker thread is launched. If this happens, it means
 * that the {@link HttpConnection} will probably have accepted another request and the timeout checker will actually be
 * timing out a different request than the one originally intended.
 * <p/>
 * Having a per-request checker does hurt performance a bit but it avoids the aforementioned problem. Besides, this
 * small performance slowdown only occurs when the request-response cycle takes 0.01~ to complete which, in real
 * scenarios, is extremely unlikely to ever happen.
 *
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class TimeoutChecker implements Runnable, HttpRequestFutureListener {

    // internal vars --------------------------------------------------------------------------------------------------

    private final HttpRequestContext request;
    private final CountDownLatch latch;

    // constructors ---------------------------------------------------------------------------------------------------

    public TimeoutChecker(HttpRequestContext request) {
        this.request = request;
        this.latch = new CountDownLatch(1);
    }

    // Runnable -------------------------------------------------------------------------------------------------------

    @SuppressWarnings({"unchecked"})
    @Override
    public void run() {
        this.request.getFuture().addListener(this);

        // If operation completed meanwhile, operationComplete() will have been called, the latch will have been
        // counted down to zero and latch.await() will return immediately with true so everything's perfect even in
        // that twisted scenario.
        try {
            if (this.latch.await(this.request.getTimeout(), TimeUnit.MILLISECONDS)) {
                // Explicitly released before timing out, nothing to do, request already executed or failed.
                return;
            }
        } catch (InterruptedException e) {
            Thread.interrupted();
        }

        // Request timed out...
        // If the future has already been unlocked, then no harm is done by calling this method as it won't produce
        // any side effects.
        this.request.getFuture().setFailure(HttpRequestFuture.TIMED_OUT);
    }

    // HttpRequestFutureListener ----------------------------------------------------------------------------------

    @Override
    public void operationComplete(HttpRequestFuture httpRequestFuture) throws Exception {
        this.latch.countDown();
    }
}
