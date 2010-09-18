/*
 * Copyright 2010 Bruno de Carvalho
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.factor45.hotpotato.request;

import org.factor45.hotpotato.logging.Logger;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of {@link HttpRequestFuture}.
 * <p/>
 * <em>This class's synchronisation and wait logic is heavily based on Netty's
 * <a href="http://docs.jboss.org/netty/3.2/api/org/jboss/netty/channel/DefaultChannelFuture.html">
 *   {@code ChannelFuture}
 * </a>.</em>
 *
 * <h3>Post request routines</h3>
 * Since you'll often need to execute some post-request code, either in case of success or failure, you can either make
 * an explicit call to one of the variants of {@link #await()}/{@link #awaitUninterruptibly()} (thus causing that
 * thread to lie dormant until either the wait period times out or the request completes somehow) or you can add an
 * asynchronous listener that will execute upon receiving a completion notification.
 *
 * <h3>Synchronous usage</h3>
 * This is the simplest form of using the {@link HttpRequestFuture} API. You {@linkplain
 * org.factor45.hotpotato.client.HttpClient#execute(String, int, org.jboss.netty.handler.codec.http.HttpRequest,
 * org.factor45.hotpotato.response.HttpResponseProcessor) sumit a request execution} and receive an instance of
 * {@link HttpRequestFuture}. You then call one of the wait methods available:
 *
 * <pre class="code">
 * HttpRequestFuture&lt;Object&gt; future = client.execute(...);
 * future.awaitUninterruptibly();
 * if (future.isSuccessful()) {
 *     // successful handling code
 * } else {
 *     // failure handling code
 * }</pre>
 *
 * This is useful in the cases you need to execute multiple sequential requests and each of them depends on the
 * previous one's result. If you're used to Apache HttpClient's blocking calls, this will be the most familiar way to
 * interact with Hotpotato.
 *
 * <h3>Asynchronous usage</h3>
 * When requests need not be chained (fire and forget with post request treatment) and only a simple routine needs to
 * be performed once they are complete, the most elegant solution is using the listeners:
 *
 * <pre class="code">
 * HttpRequestFuture&lt;Object&gt; future = client.execute(...);
 * future.addListener(new HttpRequestFutureListener&lt;Object&gt;() {
 *     void operationComplete(HttpRequestFuture&lt;T&gt; future) throws Exception {
 *        // post completion routines
 *     }
 * });</pre>
 *
 * This will liberate the thread calling execute from having to wait in a blocking call.
 *
 * <div class="note">
 * <div class="header">IMPORTANT NOTE:</div>
 * Never ever execute non CPU-bound operations inside the listener's {@code operationComplete()} method.<br />
 * This includes database accesses, reading/writing to files, anything with I/O...
 * <p/>
 * The {@code operationComplete()} method in your listener will called from one of Netty's threads! The longer Netty's
 * threads take executing your code, the longer they'll take to return to what they were meant to do.
 * </div>
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class DefaultHttpRequestFuture<T> implements HttpRequestFuture<T> {

    // constants ------------------------------------------------------------------------------------------------------

    private static final Logger LOG = Logger.getLogger(DefaultHttpRequestFuture.class);

    // configuration --------------------------------------------------------------------------------------------------

    private final boolean cancellable;

    // internal vars --------------------------------------------------------------------------------------------------

    private T result;
    private HttpResponse response;
    private Object attachment;
    private boolean done;
    private List<HttpRequestFutureListener<T>> listeners;
    private Throwable cause;
    private int waiters;
    private long executionStart;
    private long executionEnd;
    private final long creation;

    // constructors ---------------------------------------------------------------------------------------------------

    public DefaultHttpRequestFuture() {
        this(false);
    }

    public DefaultHttpRequestFuture(boolean cancellable) {
        this.cancellable = cancellable;
        this.creation = System.nanoTime();
        this.executionStart = -1;
    }

    // HttpRequestFuture ----------------------------------------------------------------------------------------------

    @Override
    public T getProcessedResult() {
        return this.result;
    }

    @Override
    public HttpResponse getResponse() {
        return this.response;
    }

    @Override
    public HttpResponseStatus getStatus() {
        if (this.response == null) {
            return null;
        }
        return this.response.getStatus();
    }

    @Override
    public int getResponseStatusCode() {
        if (this.response == null) {
            return -1;
        }

        return this.response.getStatus().getCode();
    }

    @Override
    public boolean isSuccessfulResponse() {
        int code = this.getResponseStatusCode();
        return (code >= 200) && (code <= 299);
    }

    @Override
    public void markExecutionStart() {
        this.executionStart = System.nanoTime();
    }

    @Override
    public long getExecutionTime() {
        if (this.done) {
            return this.executionStart == -1 ? 0 : (this.executionEnd - this.executionStart) / 1000000;
        } else {
            return -1;
        }
    }

    @Override
    public long getExistenceTime() {
        if (this.done) {
            return (this.executionEnd - this.creation) / 1000000;
        } else {
            return (System.nanoTime() - this.creation) / 1000000;
        }
    }

    @Override
    public boolean isDone() {
        return this.done;
    }

    @Override
    public boolean isSuccess() {
        return this.response != null;
    }

    @Override
    public boolean isCancelled() {
        return cause == CANCELLED;
    }

    @Override
    public Throwable getCause() {
        return this.cause;
    }

    @Override
    public boolean cancel() {
        if (!this.cancellable) {
            return false;
        }

        synchronized (this) {
            if (this.done) {
                return false;
            }

            this.executionEnd = System.nanoTime();
            this.cause = CANCELLED;
            this.done = true;
            if (this.waiters > 0) {
                this.notifyAll();
            }
        }

        this.notifyListeners();
        return true;
    }

    @Override
    public boolean setSuccess(T processedResponse, HttpResponse response) {
        synchronized (this) {
            if (this.done) {
                return false;
            }

            this.executionEnd = System.nanoTime();
            this.done = true;
            this.result = processedResponse;
            this.response = response;
            if (this.waiters > 0) {
                this.notifyAll();
            }
        }

        this.notifyListeners();
        return true;
    }

    @Override
    public boolean setFailure(Throwable cause) {
        synchronized (this) {
            // Allow only once.
            if (this.done) {
                return false;
            }

            this.executionEnd = System.nanoTime();
            this.cause = cause;
            this.done = true;
            if (this.waiters > 0) {
                this.notifyAll();
            }
        }

        this.notifyListeners();
        return true;
    }

    @Override
    public boolean setFailure(HttpResponse response, Throwable cause) {
        synchronized (this) {
            // Allow only once.
            if (this.done) {
                return false;
            }

            this.executionEnd = System.nanoTime();
            this.response = response;
            this.cause = cause;
            this.done = true;
            if (this.waiters > 0) {
                this.notifyAll();
            }
        }

        this.notifyListeners();
        return true;
    }

    @Override
    public void addListener(HttpRequestFutureListener<T> listener) {
        synchronized (this) {
            if (this.done) {
                this.notifyListener(listener);
            } else {
                if (this.listeners == null) {
                    this.listeners = new ArrayList<HttpRequestFutureListener<T>>(1);
                }
                this.listeners.add(listener);
            }
        }
    }

    @Override
    public void removeListener(HttpRequestFutureListener<T> listener) {
        synchronized (this) {
            if (!this.done) {
                if (this.listeners != null) {
                    this.listeners.remove(listener);
                }
            }
        }
    }

    @Override
    public Object getAttachment() {
        return attachment;
    }

    @Override
    public void setAttachment(Object attachment) {
        this.attachment = attachment;
    }

    @Override
    public HttpRequestFuture<T> await() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        synchronized (this) {
            while (!this.done) {
                waiters++;
                try {
                    this.wait();
                } finally {
                    waiters--;
                }
            }
        }
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return this.await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override
    public HttpRequestFuture<T> awaitUninterruptibly() {
        boolean interrupted = false;
        synchronized (this) {
            while (!this.done) {
                this.waiters++;
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    this.waiters--;
                }
            }
        }

        // Preserve interruption.
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return this;
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(TimeUnit.MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private void notifyListeners() {
        // This method doesn't need synchronization because:
        // 1) This method is always called after synchronized (this) block.
        //    Hence any listener list modification happens-before this method.
        // 2) This method is called only when 'done' is true.  Once 'done'
        //    becomes true, the listener list is never modified - see add/removeListener()
        if (this.listeners == null) {
            // Not testing for isEmpty as it is ultra rare someone adding a listener and then removing it...
            return;
        }

        for (HttpRequestFutureListener<T> listener : listeners) {
            this.notifyListener(listener);
        }
    }

    private void notifyListener(HttpRequestFutureListener<T> listener) {
        try {
            listener.operationComplete(this);
        } catch (Throwable t) {
            LOG.warn("An exception was thrown by an instance of {}.", t, listener.getClass().getSimpleName());
        }
    }

    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException();
        }

        long startTime = timeoutNanos <= 0 ? 0 : System.nanoTime();
        long waitTime = timeoutNanos;
        boolean interrupted = false;

        try {
            synchronized (this) {
                if (this.done) {
                    return true;
                } else if (waitTime <= 0) {
                    return this.done;
                }

                this.waiters++;
                try {
                    for (;;) {
                        try {
                            this.wait(waitTime / 1000000, (int) (waitTime % 1000000));
                        } catch (InterruptedException e) {
                            if (interruptable) {
                                throw e;
                            } else {
                                interrupted = true;
                            }
                        }

                        if (this.done) {
                            return true;
                        } else {
                            waitTime = timeoutNanos - (System.nanoTime() - startTime);
                            if (waitTime <= 0) {
                                return this.done;
                            }
                        }
                    }
                } finally {
                    this.waiters--;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // low level overrides --------------------------------------------------------------------------------------------

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder()
                .append("HttpRequestFuture{")
                .append("existenceTime=").append(this.getExistenceTime())
                .append(", executionTime=").append(this.getExecutionTime());
        if (!this.isDone()) {
            builder.append(", inProgress");
        } else if (this.isSuccess()) {
            builder.append(", succeeded (code ").append(this.response.getStatus().getCode()).append(')');
        } else {
            builder.append(", failed (").append(this.cause).append(')');
        }
        return builder.append('}').toString();
    }
}
