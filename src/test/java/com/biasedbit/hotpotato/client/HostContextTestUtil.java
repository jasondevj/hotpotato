package com.biasedbit.hotpotato.client;

import com.biasedbit.hotpotato.request.DefaultHttpRequestFuture;
import com.biasedbit.hotpotato.response.DiscardProcessor;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * @author <a href="http://bruno.biasedbit.com/">Bruno de Carvalho</a>
 */
public class HostContextTestUtil {

    public static HttpRequestContext<Object> generateDummyContext(String host, int port, int timeout) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        return new HttpRequestContext<Object>(host, port, timeout, request, new DiscardProcessor(),
                                              new DefaultHttpRequestFuture<Object>(true));
    }

    public static HttpRequestContext<Object> generateDummyContext(String host, int port) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        return new HttpRequestContext<Object>(host, port, request, new DiscardProcessor(),
                                              new DefaultHttpRequestFuture<Object>(true));
    }
}
