package org.factor45.hotpotato.request;

import java.util.EventListener;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public interface HttpRequestFutureListener<T> extends EventListener {

    void operationComplete(HttpRequestFuture<T> future) throws Exception;
}
