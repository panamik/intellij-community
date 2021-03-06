package org.jetbrains.io;

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.handler.codec.compression.ZlibDecoder;
import org.jboss.netty.handler.codec.compression.ZlibEncoder;
import org.jboss.netty.handler.codec.compression.ZlibWrapper;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpContentCompressor;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.handler.stream.ChunkedWriteHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.SocketAddress;
import java.security.KeyStore;
import java.security.Security;

@ChannelHandler.Sharable
final class PortUnificationServerHandler extends SimpleChannelUpstreamHandler {
  private static final AtomicNotNullLazyValue<SSLContext> SSL_SERVER_CONTEXT = new AtomicNotNullLazyValue<SSLContext>() {
    @NotNull
    @Override
    protected SSLContext compute() {
      String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
      if (algorithm == null) {
        algorithm = "SunX509";
      }

      try {
        KeyStore ks = KeyStore.getInstance("JKS");
        char[] password = "jetbrains".toCharArray();
        //noinspection IOResourceOpenedButNotSafelyClosed
        ks.load(getClass().getResourceAsStream("cert.jks"), password);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ks, password);
        SSLContext serverContext = SSLContext.getInstance("TLS");
        serverContext.init(kmf.getKeyManagers(), null, null);
        return serverContext;
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  };

  private final boolean detectSsl;
  private final boolean detectGzip;

  private final ChannelGroup openChannels;
  private final DelegatingHttpRequestHandler delegatingHttpRequestHandler;

  private ChannelBuffer cumulation;

  public PortUnificationServerHandler(ChannelGroup openChannels) {
    this(new DelegatingHttpRequestHandler(), openChannels, true, true);
  }

  private PortUnificationServerHandler(DelegatingHttpRequestHandler delegatingHttpRequestHandler, @Nullable ChannelGroup openChannels, boolean detectSsl, boolean detectGzip) {
    this.delegatingHttpRequestHandler = delegatingHttpRequestHandler;
    this.openChannels = openChannels;
    this.detectSsl = detectSsl;
    this.detectGzip = detectGzip;
  }

  @Override
  public void channelOpen(ChannelHandlerContext context, ChannelStateEvent e) {
    if (openChannels != null) {
      openChannels.add(e.getChannel());
    }
  }

  @Override
  public void messageReceived(
    ChannelHandlerContext context, MessageEvent e) throws Exception {
    Object m = e.getMessage();
    if (!(m instanceof ChannelBuffer)) {
      context.sendUpstream(e);
      return;
    }

    ChannelBuffer input = (ChannelBuffer) m;
    if (!input.readable()) {
      return;
    }

    if (cumulation == null) {
      if (input.readableBytes() < 5) {
        cumulation = context.getChannel().getConfig().getBufferFactory().getBuffer(8);
        cumulation.writeBytes(input);
      }
      else {
        decode(context, input, e.getRemoteAddress());
      }
    }
    else {
      if ((cumulation.readableBytes() + input.readableBytes()) < 5) {
        cumulation.writeBytes(input);
      }
      else {
        ChannelBuffer compositeBuffer = ChannelBuffers.wrappedBuffer(cumulation, input);
        cumulation = null;
        decode(context, compositeBuffer, e.getRemoteAddress());
      }
    }
  }

  private void decode(ChannelHandlerContext context, ChannelBuffer buffer, SocketAddress remoteAddress) throws Exception {
    ChannelPipeline pipeline = context.getPipeline();
    if (detectSsl && SslHandler.isEncrypted(buffer)) {
      SSLEngine engine = SSL_SERVER_CONTEXT.getValue().createSSLEngine();
      engine.setUseClientMode(false);
      pipeline.addLast("ssl", new SslHandler(engine));
      pipeline.addLast("streamer", new ChunkedWriteHandler());
      pipeline.addLast("unificationWOSsl", new PortUnificationServerHandler(delegatingHttpRequestHandler, null, false, detectGzip));
    }
    else {
      int magic1 = buffer.getUnsignedByte(buffer.readerIndex());
      int magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1);
      if (detectGzip && magic1 == 31 && magic2 == 139) {
        pipeline.addLast("gzipDeflater", new ZlibEncoder(ZlibWrapper.GZIP));
        pipeline.addLast("gzipInflater", new ZlibDecoder(ZlibWrapper.GZIP));
        pipeline.addLast("unificationWOGzip", new PortUnificationServerHandler(delegatingHttpRequestHandler, null, detectSsl, false));
      }
      else {
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", new HttpChunkAggregator(1048576));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("deflater", new HttpContentCompressor());
        pipeline.addLast("handler", delegatingHttpRequestHandler);
      }
    }
    // must be after new channels handlers addition (netty bug?)
    pipeline.remove(this);
    Channels.fireMessageReceived(context, buffer, remoteAddress);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext context, ExceptionEvent event) throws Exception {
    BuiltInServer.LOG.error(event.getCause());
  }
}