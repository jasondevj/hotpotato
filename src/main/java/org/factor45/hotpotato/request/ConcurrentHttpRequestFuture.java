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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of {@link HttpRequestFuture} that resorts to stuff in {@linkplain java.util.concurrent} package.
 * <p/>
 * Uses less synchronisation blocks which is potentially faster for high concurrency scenarios. 
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class ConcurrentHttpRequestFuture<T> implements HttpRequestFuture<T> {

    // constants ------------------------------------------------------------------------------------------------------

    private static final Logger LOG = Logger.getLogger(ConcurrentHttpRequestFuture.class);

    // configuration --------------------------------------------------------------------------------------------------

    private final boolean cancellable;

    // internal vars --------------------------------------------------------------------------------------------------

    private final CountDownLatch waitLatch;
    private T result;
    private HttpResponse response;
    private Object attachment;
    private final AtomicBoolean done;
    private final List<HttpRequestFutureListener<T>> listeners;
    private Throwable cause;
    private long executionStart;
    private long executionEnd;
    private final long creation;

    // constructors ---------------------------------------------------------------------------------------------------

    public ConcurrentHttpRequestFuture() {
        this(false);
    }

    public ConcurrentHttpRequestFuture(boolean cancellable) {
        this.cancellable = cancellable;
        this.creation = System.nanoTime();
        this.executionStart = -1;
        this.done = new AtomicBoolean(false);
        // It's just a couple of bytes and memory is cheaper than CPU...
        this.listeners = new ArrayList<HttpRequestFutureListener<T>>(2);

        this.waitLatch = new CountDownLatch(1);
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
        if (this.done.get()) {
            return this.executionStart == -1 ? 0 : (this.executionEnd - this.executionStart) / 1000000;
        } else {
            return -1;
        }
    }

    @Override
    public long getExistenceTime() {
        if (this.done.get()) {
            return (this.executionEnd - this.creation) / 1000000;
        } else {
            return (System.nanoTime() - this.creation) / 1000000;
        }
    }

    @Override
    public boolean isDone() {
        return this.done.get();
    }

    @Override
    public boolean isSuccess() {
        return this.response != null;
    }

    @Override
    public boolean isCancelled() {
        return this.cause == CANCELLED;
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

        // Get previous value and set to true
        if (this.done.getAndSet(true)) {
            // If previous value was already true, then bail out.
            return false;
        }

        this.executionEnd = System.nanoTime();
        this.cause = CANCELLED;
        this.waitLatch.countDown();

        this.notifyListeners();
        return true;
    }

    @Override
    public boolean setSuccess(T processedResponse, HttpResponse response) {
        // Get previous value and set to true
        if (this.done.getAndSet(true)) {
            // If previous value was already true, then bail out.
            return false;
        }

        this.executionEnd = System.nanoTime();
        this.result = processedResponse;
        this.response = response;
        this.waitLatch.countDown();

        this.notifyListeners();
        return true;
    }

    @Override
    public boolean setFailure(Throwable cause) {
        // Get previous value and set to true
        if (this.done.getAndSet(true)) {
            // If previous value was already true, then bail out.
            return false;
        }

        this.executionEnd = System.nanoTime();
        this.cause = cause;
        this.waitLatch.countDown();

        this.notifyListeners();
        return true;
    }

    @Override
    public boolean setFailure(HttpResponse response, Throwable cause) {
        // Get previous value and set to true
        if (this.done.getAndSet(true)) {
            // If previous value was already true, then bail out.
            return false;
        }

        this.executionEnd = System.nanoTime();
        this.response = response;
        this.cause = cause;
        this.waitLatch.countDown();

        this.notifyListeners();
        return true;
    }

    @Override
    public void addListener(HttpRequestFutureListener<T> listener) {
        if (this.done.get()) {
            this.notifyListener(listener);
            return;
        }

        synchronized (this.listeners) {
            if (this.done.get()) {
                this.notifyListener(listener);
                return;
            }

            this.listeners.add(listener);
        }
    }

    @Override
    public void removeListener(HttpRequestFutureListener<T> listener) {
        if (this.done.get()) {
            return;
        }

        synchronized (this.listeners) {
            if (!this.done.get()) {
                this.listeners.remove(listener);
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

        this.waitLatch.await();

        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return this.waitLatch.await(timeout, unit);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return this.waitLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public HttpRequestFuture<T> awaitUninterruptibly() {
        boolean interrupted = false;
        while (!this.done.get() || (this.waitLatch.getCount() > 0)) {
            try {
                this.waitLatch.await();
            } catch (InterruptedException e) {
                // Stubborn basterd!
                interrupted = true;
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
        long start;
        long waitTime = unit.toNanos(timeout);
        boolean interrupted = false;
        boolean onTime = false;

        while (!this.done.get() && (waitTime > 0)) {
            start = System.nanoTime();
            try {
                onTime = this.waitLatch.await(waitTime, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                // subtract delta to remaining wait time
                waitTime -= (System.nanoTime() - start);
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.interrupted();
        }

        return onTime;
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return this.awaitUninterruptibly(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    // private helpers ------------------------------------------------------------------------------------------------

    private void notifyListeners() {
        synchronized (this.listeners) {
            for (HttpRequestFutureListener<T> listener : this.listeners) {
                this.notifyListener(listener);
            }
        }
    }

    private void notifyListener(HttpRequestFutureListener<T> listener) {
        try {
            listener.operationComplete(this);
        } catch (Throwable t) {
            LOG.warn("An exception was thrown by an instance of {}.", t, listener.getClass().getSimpleName());
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
