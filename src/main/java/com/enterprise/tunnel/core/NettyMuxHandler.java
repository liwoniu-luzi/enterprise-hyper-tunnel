package com.enterprise.tunnel.core;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * Netty 管道内嵌分流处理器（零额外端口占用，直接与 Minecraft 游戏协议共存）
 * 1. 识别并拦截 WebSocket (VLESS-WS)
 * 2. 识别并拦截 HTTP Stream (VLESS-XHTTP)
 * 3. 其余 Minecraft 游戏连接完全原样放行给服务端游戏核心
 */
public class NettyMuxHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = Logger.getLogger("EnterpriseTunnel");
    private final byte[] expectedUuidBytes;
    private final String expectedWsPath;
    private final String expectedXhttpPath;

    public NettyMuxHandler(String uuidStr, String wsPath, String xhttpPath) {
        this.expectedUuidBytes = VlessCodec.uuidToBytes(uuidStr);
        this.expectedWsPath = wsPath.startsWith("/") ? wsPath : "/" + wsPath;
        this.expectedXhttpPath = xhttpPath.startsWith("/") ? xhttpPath : "/" + xhttpPath;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf buf) {
            if (buf.readableBytes() >= 4) {
                buf.markReaderIndex();
                byte b0 = buf.readByte();
                byte b1 = buf.readByte();
                byte b2 = buf.readByte();
                byte b3 = buf.readByte();
                buf.resetReaderIndex();

                // 探测 HTTP 请求特征 (GET, POST, HEAD, OPTIONS 等)
                boolean isHttp = (b0 == 'G' && b1 == 'E' && b2 == 'T') ||
                                 (b0 == 'P' && b1 == 'O' && b2 == 'S' && b3 == 'T') ||
                                 (b0 == 'H' && b1 == 'E' && b2 == 'A') ||
                                 (b0 == 'O' && b1 == 'P' && b2 == 'T');

                if (isHttp) {
                    // 确认是代理连接，动态装配 HTTP/WS 解码器并截胡
                    ChannelPipeline p = ctx.pipeline();
                    p.addBefore(ctx.name(), "http-codec", new HttpServerCodec());
                    p.addBefore(ctx.name(), "http-aggregator", new HttpObjectAggregator(65536));
                    p.addBefore(ctx.name(), "http-bridge-handler", new HttpTunnelBridge(expectedUuidBytes, expectedWsPath, expectedXhttpPath));
                    p.remove(this); // 从管道中移除嗅探器
                    ctx.fireChannelRead(msg);
                    return;
                }
            }
        }

        // 纯 Minecraft 原生数据包，完全放行给游戏引擎处理（确保假人与普通玩家不受任何影响）
        super.channelRead(ctx, msg);
    }

    /**
     * HTTP / WebSocket 握手与数据转发桥接
     */
    private static class HttpTunnelBridge extends SimpleChannelInboundHandler<Object> {
        private final byte[] expectedUuidBytes;
        private final String expectedWsPath;
        private final String expectedXhttpPath;
        private WebSocketServerHandshaker handshaker;
        private Socket upstreamSocket;
        private OutputStream upstreamOut;
        private boolean vlessHeaderParsed = false;

        public HttpTunnelBridge(byte[] uuidBytes, String wsPath, String xhttpPath) {
            this.expectedUuidBytes = uuidBytes;
            this.expectedWsPath = wsPath;
            this.expectedXhttpPath = xhttpPath;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof FullHttpRequest req) {
                handleHttpRequest(ctx, req);
            } else if (msg instanceof WebSocketFrame frame) {
                handleWebSocketFrame(ctx, frame);
            }
        }

        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
            String uri = req.uri();

            // 1. WebSocket 模式升级
            if (req.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true)
                    || uri.startsWith(expectedWsPath)) {
                WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                        getWebSocketLocation(req), null, true, 65536);
                this.handshaker = wsFactory.newHandshaker(req);
                if (this.handshaker == null) {
                    WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
                } else {
                    this.handshaker.handshake(ctx.channel(), req);
                }
                return;
            }

            // 2. XHTTP / 流式分块传输模式
            if (uri.startsWith(expectedXhttpPath) || uri.contains("/assets/minecraft/textures") || uri.contains("/telemetry")) {
                ByteBuf content = req.content();
                if (content.isReadable()) {
                    byte[] raw = new byte[content.readableBytes()];
                    content.readBytes(raw);
                    handleVlessPayload(ctx, raw, true);
                }
                return;
            }

            // 伪装 200 OK
            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK,
                    Unpooled.wrappedBuffer("{\"status\":\"healthy\",\"service\":\"Minecraft Network Telemetry\"}".getBytes()));
            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8");
            ctx.writeAndFlush(resp).addListener(ChannelFutureListener.CLOSE);
        }

        private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
            if (frame instanceof CloseWebSocketFrame) {
                if (handshaker != null) handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
                closeUpstream();
                return;
            }
            if (frame instanceof PingWebSocketFrame) {
                ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
                return;
            }
            if (frame instanceof BinaryWebSocketFrame binary) {
                ByteBuf buf = binary.content();
                byte[] raw = new byte[buf.readableBytes()];
                buf.readBytes(raw);
                handleVlessPayload(ctx, raw, false);
            }
        }

        private void handleVlessPayload(ChannelHandlerContext ctx, byte[] data, boolean isXhttp) {
            synchronized (this) {
                if (!vlessHeaderParsed) {
                    try {
                        VlessCodec.TargetDestination dest = VlessCodec.parseRequest(data, expectedUuidBytes);
                        vlessHeaderParsed = true;

                        upstreamSocket = new Socket();
                        upstreamSocket.setTcpNoDelay(true);
                        upstreamSocket.connect(new InetSocketAddress(dest.host, dest.port), 10000);
                        upstreamOut = upstreamSocket.getOutputStream();

                        // 回送 VLESS 响应头 (0x00, 0x00)
                        byte[] respHeader = VlessCodec.createResponseHeader();
                        if (isXhttp) {
                            FullHttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(respHeader));
                            resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/octet-stream");
                            ctx.writeAndFlush(resp);
                        } else {
                            ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(respHeader)));
                        }

                        if (dest.payload != null && dest.payload.length > 0) {
                            upstreamOut.write(dest.payload);
                            upstreamOut.flush();
                        }

                        // 异步虚拟线程读取目标服务器上行数据并送回客户端
                        Thread.ofVirtual().start(() -> {
                            byte[] buffer = new byte[16384];
                            try (InputStream in = upstreamSocket.getInputStream()) {
                                int r;
                                while ((r = in.read(buffer)) != -1 && ctx.channel().isActive()) {
                                    byte[] chunk = new byte[r];
                                    System.arraycopy(buffer, 0, chunk, 0, r);
                                    ctx.executor().execute(() -> {
                                        if (isXhttp) {
                                            ctx.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(chunk)));
                                        } else {
                                            ctx.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(chunk)));
                                        }
                                    });
                                }
                            } catch (Exception ignored) {
                            } finally {
                                closeUpstream();
                                ctx.close();
                            }
                        });

                    } catch (Exception e) {
                        closeUpstream();
                        ctx.close();
                    }
                } else {
                    if (upstreamOut != null) {
                        try {
                            upstreamOut.write(data);
                            upstreamOut.flush();
                        } catch (Exception e) {
                            closeUpstream();
                            ctx.close();
                        }
                    }
                }
            }
        }

        private void closeUpstream() {
            if (upstreamSocket != null && !upstreamSocket.isClosed()) {
                try {
                    upstreamSocket.close();
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            closeUpstream();
            super.channelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            closeUpstream();
            ctx.close();
        }

        private String getWebSocketLocation(FullHttpRequest req) {
            String location = req.headers().get(HttpHeaderNames.HOST);
            return "ws://" + location + expectedWsPath;
        }
    }
}
