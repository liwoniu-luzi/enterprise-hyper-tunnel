package com.enterprise.tunnel.core;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 纯 Java 原生高性能 WebSocket 转发引擎（全静默无日志）
 */
public class RobustWsServer extends WebSocketServer {

    private final byte[] expectedUuidBytes;
    private final String expectedPath;
    private final Map<WebSocket, ClientSession> sessionMap = new ConcurrentHashMap<>();

    private static class ClientSession {
        boolean headerParsed = false;
        Socket upstreamSocket;
        OutputStream upstreamOut;
    }

    public RobustWsServer(int port, String uuidStr, String path) {
        super(new InetSocketAddress(port));
        this.expectedUuidBytes = VlessCodec.uuidToBytes(uuidStr);
        this.expectedPath = (path == null || path.isEmpty()) ? "/benchmark" : (path.startsWith("/") ? path : "/" + path);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String descriptor = handshake.getResourceDescriptor();
        if (!descriptor.startsWith(expectedPath)) {
            conn.close(1008, "Path mismatch");
            return;
        }
        sessionMap.put(conn, new ClientSession());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientSession session = sessionMap.remove(conn);
        closeUpstream(session);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        ClientSession session = sessionMap.get(conn);
        if (session == null) return;

        byte[] rawBytes = new byte[message.remaining()];
        message.get(rawBytes);

        synchronized (session) {
            if (!session.headerParsed) {
                try {
                    VlessCodec.TargetDestination dest = VlessCodec.parseRequest(rawBytes, expectedUuidBytes);
                    session.headerParsed = true;

                    Socket targetSocket = new Socket();
                    targetSocket.setTcpNoDelay(true);
                    targetSocket.connect(new InetSocketAddress(dest.host, dest.port), 10000);
                    session.upstreamSocket = targetSocket;
                    session.upstreamOut = targetSocket.getOutputStream();

                    // 回送 VLESS 响应头 (0x00, 0x00)
                    byte[] respHeader = VlessCodec.createResponseHeader();
                    conn.send(ByteBuffer.wrap(respHeader));

                    if (dest.payload != null && dest.payload.length > 0) {
                        session.upstreamOut.write(dest.payload);
                        session.upstreamOut.flush();
                    }

                    Thread.ofVirtual().start(() -> handleUpstreamRead(conn, session, targetSocket));

                } catch (Exception e) {
                    closeUpstream(session);
                    conn.close(1008, "Protocol error");
                }
            } else {
                if (session.upstreamOut != null) {
                    try {
                        session.upstreamOut.write(rawBytes);
                        session.upstreamOut.flush();
                    } catch (Exception e) {
                        closeUpstream(session);
                        conn.close();
                    }
                }
            }
        }
    }

    private void handleUpstreamRead(WebSocket conn, ClientSession session, Socket targetSocket) {
        byte[] buffer = new byte[16384];
        try (InputStream in = targetSocket.getInputStream()) {
            int read;
            while ((read = in.read(buffer)) != -1 && conn.isOpen()) {
                byte[] chunk = new byte[read];
                System.arraycopy(buffer, 0, chunk, 0, read);
                conn.send(ByteBuffer.wrap(chunk));
            }
        } catch (Exception ignored) {
        } finally {
            closeUpstream(session);
            if (conn.isOpen()) {
                conn.close();
            }
        }
    }

    private void closeUpstream(ClientSession session) {
        if (session != null && session.upstreamSocket != null) {
            try {
                session.upstreamSocket.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn != null) {
            ClientSession session = sessionMap.remove(conn);
            closeUpstream(session);
        }
    }

    @Override
    public void onStart() {
    }
}
