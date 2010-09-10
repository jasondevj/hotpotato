package org.factor45.hotpotato.pipelining;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://bruno.factor45.org/">Bruno de Carvalho</a>
 */
public class Http11PipeliningTestServer {

    // configuration ---------------------------------------------------------

    private final String host;
    private final int port;

    // internal vars ---------------------------------------------------------

    private ServerBootstrap bootstrap;
    private DefaultChannelGroup channelGroup;
    private MessageCounter packetCounter;
    private MessageCounter httpRequestCounter;
    private boolean running;

    // constructors ----------------------------------------------------------

    public Http11PipeliningTestServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Http11PipeliningTestServer(int port) {
        this(null, port);
    }

    // public methods --------------------------------------------------------

    public boolean init() {
        NioServerSocketChannelFactory channelFactory =
                new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool());

        this.bootstrap = new ServerBootstrap(channelFactory);

        this.packetCounter = new MessageCounter();
        this.httpRequestCounter = new MessageCounter();
        this.bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("packetCounter", packetCounter);
                pipeline.addLast("encoder", new HttpResponseEncoder());
                pipeline.addLast("decoder", new HttpRequestDecoder());
                pipeline.addLast("httpRequestCounter", httpRequestCounter);
                pipeline.addLast("handler", new ServerHandler());
                return pipeline;
            }
        });
        this.channelGroup = new DefaultChannelGroup("pipelining-server");

        SocketAddress bindAddress;
        if (this.host != null) {
            bindAddress = new InetSocketAddress(this.host, this.port);
        } else {
            bindAddress = new InetSocketAddress(this.port);
        }
        Channel serverChannel = this.bootstrap.bind(bindAddress);
        this.channelGroup.add(serverChannel);

        return (this.running = serverChannel.isBound());
    }

    public void terminate() {
        if (!this.running) {
            return;
        }

        System.out.println("Served " + this.httpRequestCounter.getCount() +
                           " HTTP requests over " +
                           this.packetCounter.getCount() + " TCP packets.");
        this.running = false;
        this.channelGroup.close().awaitUninterruptibly();
        this.bootstrap.releaseExternalResources();
    }

    // private classes -------------------------------------------------------

    private final class ServerHandler extends SimpleChannelUpstreamHandler {

        // SimpleChannelUpstreamHandler --------------------------------------

        @Override
        public void channelConnected(ChannelHandlerContext ctx,
                                     ChannelStateEvent e) throws Exception {
            channelGroup.add(e.getChannel());
        }

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            HttpRequest request = (HttpRequest) e.getMessage();
            HttpResponse response =
                    new DefaultHttpResponse(request.getProtocolVersion(),
                                            HttpResponseStatus.OK);

            // Write the requested URI as response body
            response.setContent(ChannelBuffers
                    .copiedBuffer(request.getUri(), CharsetUtil.UTF_8));
            response.setHeader(HttpHeaders.Names.CONTENT_TYPE,
                               "text/plain; charset=UTF-8");

            boolean keepAlive = HttpHeaders.isKeepAlive(request);
            if (keepAlive) {
                response.setHeader(HttpHeaders.Names.CONTENT_LENGTH,
                                   response.getContent().readableBytes());
            }

            ChannelFuture f = e.getChannel().write(response);
            // Write the response & close the connection after the write op.
            if (!keepAlive) {
                f.addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx,
                                    ExceptionEvent e) throws Exception {

            System.err.println("Exception caught on HTTP connection from " +
                               e.getChannel().getRemoteAddress() +
                               "; closing channel.");
            e.getCause().printStackTrace();
            if (e.getChannel().isConnected()) {
                e.getChannel().close();
            }
        }
    }

    private static final class MessageCounter
            extends SimpleChannelUpstreamHandler {

        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public void messageReceived(ChannelHandlerContext ctx, MessageEvent e)
                throws Exception {
            this.counter.incrementAndGet();
            super.messageReceived(ctx, e);
        }

        public int getCount() {
            return counter.get();
        }
    }

    // main ------------------------------------------------------------------

    public static void main(String[] args) {
        final Http11PipeliningTestServer server =
                new Http11PipeliningTestServer(8080);
        if (!server.init()) {
            System.err.println("Failed to initialise server.");
        } else {
            System.out.println("Server bound.");
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                server.terminate();
            }
        });
    }
}
