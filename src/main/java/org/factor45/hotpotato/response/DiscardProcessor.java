package org.factor45.hotpotato.response;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * {@link HttpResponseProcessor} implementation that always discards the response body
 * ({@link #willProcessResponse(org.jboss.netty.handler.codec.http.HttpResponse)} always returns {@code false}).
 * <p/>
 * Always returns {@code null} when  {@link #getProcessedResponse()} is called and performs no action when
 * {@link #addData(org.jboss.netty.buffer.ChannelBuffer)} or {@link #addLastData(org.jboss.netty.buffer.ChannelBuffer)}
 * are called.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class DiscardProcessor implements HttpResponseProcessor<Object> {

    // HttpResponseProcessor ------------------------------------------------------------------------------------------

    @Override
    public boolean willProcessResponse(HttpResponse response) {
        return false;
    }

    @Override
    public void addData(ChannelBuffer content) throws Exception {
    }

    @Override
    public void addLastData(ChannelBuffer content) throws Exception {
    }

    @Override
    public Object getProcessedResponse() {
        return null;
    }
}
