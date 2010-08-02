package org.factor45.hotpotato.response;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.CharsetUtil;

/**
 * {@link HttpResponseProcessor} that consumes a body and transforms it into a UTF8 string.
 *
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class BodyAsStringProcessor implements HttpResponseProcessor<String> {

    // internal vars --------------------------------------------------------------------------------------------------

    private ChannelBuffer buffer;
    private boolean finished;
    private String result;

    // HttpResponseProcessor ------------------------------------------------------------------------------------------

    @Override
    public boolean willProcessResponse(HttpResponse response) {
        long length = HttpHeaders.getContentLength(response);
        if (length > Integer.MAX_VALUE) {
            return false;
        }

        if (length > 0) {
            // HTTP 1.1
            this.buffer = ChannelBuffers.buffer((int) length);
            return true;
        } else if ((response.getContent() != null) && (response.getContent().readableBytes() > 0)) {
            // HTTP 1.0
            this.buffer = response.getContent();
            this.result = this.buffer.toString(CharsetUtil.UTF_8);
            this.finished = true;
            return true;
        }

        return false;
    }

    @Override
    public void addData(ChannelBuffer content) throws Exception {
        if (!this.finished) {
            this.buffer.writeBytes(content);
        }
    }

    @Override
    public void addLastData(ChannelBuffer content) throws Exception {
        if (!this.finished) {
            this.buffer.writeBytes(content);
            this.result = this.buffer.toString(CharsetUtil.UTF_8);
            this.finished = true;
        }
    }

    @Override
    public String getProcessedResponse() {
        return this.result;
    }
}
