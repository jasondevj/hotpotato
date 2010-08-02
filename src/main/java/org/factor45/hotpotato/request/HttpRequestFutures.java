package org.factor45.hotpotato.request;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class HttpRequestFutures {

    // constructors ---------------------------------------------------------------------------------------------------

    private HttpRequestFutures() {
    }

    // public static methods ------------------------------------------------------------------------------------------

    public static <T> HttpRequestFuture<T> future() {
        return new DefaultHttpRequestFuture<T>(false);
    }

    public static <T> HttpRequestFuture<T> future(boolean cancelable) {
        return new DefaultHttpRequestFuture<T>(cancelable);
    }
}
