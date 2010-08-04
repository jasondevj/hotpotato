package org.factor45.hotpotato.client;

import org.factor45.hotpotato.request.HttpRequestFutures;
import org.factor45.hotpotato.response.DiscardProcessor;
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpVersion;

/**
 * @author <a:mailto="bruno.carvalho@wit-software.com" />Bruno de Carvalho</a>
 */
public class HostContextTestUtil {

    public static HttpRequestContext<Object> generateDummyContext(String host, int port, int timeout) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        return new HttpRequestContext<Object>(host, port, timeout, request, new DiscardProcessor(),
                                              HttpRequestFutures.future(true));
    }

    public static HttpRequestContext<Object> generateDummyContext(String host, int port) {
        HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/");
        return new HttpRequestContext<Object>(host, port, request, new DiscardProcessor(),
                                              HttpRequestFutures.future(true));
    }
}
