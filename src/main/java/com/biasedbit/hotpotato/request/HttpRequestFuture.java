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

package com.biasedbit.hotpotato.request;

import com.biasedbit.hotpotato.client.HttpClient;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.util.concurrent.TimeUnit;

/**
 * The result of submitting a request to a {@link com.biasedbit.hotpotato.client.HttpClient}.
 *
 * Much like Netty's {@code ChannelFuture} for I/O operations, every time you submit a request to a
 * {@link com.biasedbit.hotpotato.client.HttpClient}, instead of having to wait for the request to complete, you will
 * receive an instance of {@link HttpRequestFuture}.
 * <p/>
 * <em>Please refer to <a href="http://docs.jboss.org/netty/3.2/api/org/jboss/netty/channel/ChannelFuture.html">
 * Netty's {@code ChannelFuture} API</a> for a detailed overview on how this future works. Given that this class is
 * heavily based on Netty's {@code ChannelFuture}, all of its the functional principles also apply to
 * {@link HttpRequestFuture}.</em>
 *
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public interface HttpRequestFuture<T> {

    /**
     * Error cause for interrupted requests via {@code Thread.interrupt()}.
     */
    static final Throwable INTERRUPTED = new Throwable("Interrupted");
    /**
     * Error cause for cancelled requests via {@code future.cancel()}.
     */
    static final Throwable CANCELLED = new Throwable("Cancelled");
    /**
     * Error cause for requests that fail because no connection can be established to the remote host.
     */
    static final Throwable CANNOT_CONNECT = new Throwable("Cannot connect");
    /**
     * Error cause for requests that could not execute due to connection to which they were allocated being lost.
     */
    static final Throwable CONNECTION_LOST = new Throwable("Connection lost");
    /**
     * Error cause for requests that cannot execute because the connection to which they were allocated is not connected
     * or is shutting down.
     */
    static final Throwable SHUTTING_DOWN = new Throwable("Shutting down");
    /**
     * Error cause for requests that are fed to {@link com.biasedbit.hotpotato.client.connection.HttpConnection}s while
     * their {@code isAvailable()} method is returning {@code false}.
     */
    static final Throwable EXECUTION_REJECTED = new Throwable("Execution rejected by connection");
    /**
     * Error cause for requests whose response takes longer than the limit established.
     * <p/>
     * If a {@linkplain com.biasedbit.hotpotato.response.HttpResponseProcessor response processor} chooses to discard the
     * body of the request, the timeout checker is cancelled once the headers are received.
     * <p/>
     * If the processor chooses to consume the body, then the timeout checker is only cancelled after the whole body is
     * consumed.
     */
    static final Throwable TIMED_OUT = new Throwable("Request execution timed out");

    static final Throwable INVALID_REDIRECT = new Throwable("Redirect without Location header");

    /**
     * Returns the result processed by the {@link com.biasedbit.hotpotato.response.HttpResponseProcessor}, if any.
     *
     * @return Processed result.
     */
    T getProcessedResult();

    /**
     * Returns the {@link org.jboss.netty.handler.codec.http.HttpRequest} object, if any.
     *
     * @return HTTP response.
     */
    HttpResponse getResponse();

    /**
     * Alias for {@code future.getResponse().getStatus()}.
     *
     * @return status of the response or {@code null} if {@link #getResponse()} would yield {@code null}.
     */
    HttpResponseStatus getStatus();
    /**
     * Handy alias for {@code future.getResponse().getStatus().getCode()}
     *
     * @return status code value or {@code -1} if response is {@code null} when this method is called.
     */
    int getResponseStatusCode();

    /**
     * Alias to check if the response code was >= 200 and <= 299.
     *
     * @return {@code true} if response status code was >= 200 and <= 299, {@code false} otherwise.
     */
    boolean isSuccessfulResponse();

    /**
     * Used by the {@link com.biasedbit.hotpotato.client.connection.HttpConnection} implementations to mark request
     * execution start instant.
     */
    void markExecutionStart();

    /**
     * Returns the request's total execution time from the time of writing to the socket until the future is released,
     * in milliseconds. May return 0 for implementations that disregard these metrics.
     *
     * @return Execution time, in milliseconds.
     */
    long getExecutionTime();

    /**
     * Returns the total time the request lived for, including time spent in queues. Basically, this is the time spent
     * between the creation of the future and its termination.
     * <p/>
     * If the request hasn't terminated upon calling this method, the implementation returns
     * {@code System.nanoTime()- creationInstant / 1000000} (milliseconds).
     * <p/>
     * <strong>NOTE: </strong>existence time - execution time = time spent in queues/handling.
     * <p/>
     * Implementations may disregard this method and always return 0 or -1.
     *
     * @return Elapsed time, in milliseconds, since the future object was created.
     */
    long getExistenceTime();

    /**
     * Returns whether the request associated with this future is done or not.
     *
     * @return {@code true} if request terminated (successfully or not), {@code false} otherwise.
     */
    boolean isDone();

    /**
     * Returns whether the request associated with this future was successfully completed or not.
     *
     * @return {@code true} if request was successfully completed, {@code false} otherwise.
     */
    boolean isSuccess();

    /**
     * Returns whether the request associated with this future was cancelled or not.
     *
     * @return {@code true} if request was cancelled, {@code false} otherwise.
     */
    boolean isCancelled();

    /**
     * Returns the failure cause for the request associated with this future, if any.
     *
     * @return The failure cause.
     */
    Throwable getCause();

    /**
     * Cancels the HTTP request associated with this future and notifies all listeners if canceled successfully.
     *
     * @return {@code true} if and only if the operation has been canceled. {@code false} if the operation can't be
     *         canceled or is already completed.
     */
    boolean cancel();

    /**
     * Marks this future as a success and notifies all listeners.
     * <p/>
     * <strong>NOTE: </strong> The fact that this method is called "success" does not mean that the HTTP response code
     * was {@code 200}!
     *
     * @param processedResult Value returned by the {@link com.biasedbit.hotpotato.response.HttpResponseProcessor}
     *                        associated with this request.
     * @param response        The HTTP response.
     *
     * @return {@code true} if and only if successfully marked this future as a success. Otherwise {@code false} because
     *         this future is already marked as either a success or a failure.
     */
    boolean setSuccess(T processedResult, HttpResponse response);

    /**
     * Marks this future as a failure and notifies all listeners.
     *
     * @param cause The cause of the failure.
     *
     * @return {@code true} if and only if successfully marked this future as a failure. Otherwise {@code false} because
     *         this future is already marked as either a success or a failure.
     */
    boolean setFailure(Throwable cause);

    /**
     * Same as {@link #setFailure(Throwable)}, except it also records the state of the received response.
     * <p/>
     * This variant is useful to distinguish between errors executing the request itself or errors ocurred during
     * response parsing.
     *
     * @param response The {@link HttpResponse} received before the error.
     * @param cause The cause of the failure.
     *
     * @return {@code true} if and only if successfully marked this future as a failure. Otherwise {@code false} because
     *         this future is already marked as either a success or a failure.
     */
    boolean setFailure(HttpResponse response, Throwable cause);

    /**
     * Add a listener to this future.
     * <p/>
     * If the future is already complete (i.e. {@link #isDone()} returns {@code true}) then the listener is immediately
     * notified of the operation completion, thus avoiding code stalls waiting for a notification that was already
     * triggered.
     *
     * @param listener The listener to add.
     */
    void addListener(HttpRequestFutureListener<T> listener);

    /**
     * Detaches a listened from this future.
     *
     * @param listener The listener to remove.
     */
    void removeListener(HttpRequestFutureListener<T> listener);

    /**
     * Adds an attachment to this future.
     *
     * @param attachment Attachment to add.
     */
    void setAttachment(Object attachment);

    /**
     * Retrieves the attachment of this future, if any.
     *
     * @return The attachment of this future.
     */
    Object getAttachment();

    /**
     * Waits for this future to be completed.
     *
     * @return This instance (allows call chaining).
     *
     * @throws InterruptedException if the current thread was interrupted
     */
    HttpRequestFuture<T> await() throws InterruptedException;

    /**
     * Waits for this future to be completed without interruption. This method catches an {@link InterruptedException}
     * and discards it silently.
     *
     * @return This instance (allows call chaining).
     */
    HttpRequestFuture<T> awaitUninterruptibly();

    /**
     * Waits for this future to be completed within the specified time limit.
     *
     * @param timeout Time alloted for the wait.
     * @param unit    Unit of time to wait.
     *
     * @return {@code true} if and only if the future was completed within the specified time limit
     *
     * @throws InterruptedException if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for this future to be completed within the specified time limit.
     *
     * @param timeoutMillis Time alloted for the wait, in milliseconds.
     *
     * @return {@code true} if and only if the future was completed within the specified time limit
     *
     * @throws InterruptedException if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * Waits for this future to be completed within the specified time limit without interruption. This method catches
     * an {@link InterruptedException} and discards it silently.
     *
     * @param timeout Time alloted for the wait.
     * @param unit    Unit of time to wait.
     *
     * @return {@code true} if and only if the future was completed within the specified time limit
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this future to be completed within the specified time limit without interruption.  This method catches
     * an {@link InterruptedException} and discards it silently.
     *
     * @param timeoutMillis Time alloted for the wait, in milliseconds.
     *
     * @return {@code true} if and only if the future was completed within the specified time limit
     */
    boolean awaitUninterruptibly(long timeoutMillis);

    HttpClient getClient();

    void setClient(HttpClient client);
}
