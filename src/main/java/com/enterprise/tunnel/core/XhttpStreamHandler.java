package com.enterprise.tunnel.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * 纯 Java 原生 VLESS-XHTTP / SplitHTTP 分块流式转发引擎（带动态随机填充与材质包/遥测伪装）
 */
public class XhttpStreamHandler implements HttpHandler {

    private final byte[] expectedUuidBytes;
    private final String expectedPath;
    private final Logger logger;
    private final SecureRandom secureRandom = new SecureRandom();
    private HttpServer server;

    public XhttpStreamHandler(int port, String uuidStr, String path, Logger logger) throws IOException {
        this.expectedUuidBytes = VlessCodec.uuidToBytes(uuidStr);
        this.expectedPath = (path == null || path.isEmpty()) ? "/xhttp" : (path.startsWith("/") ? path : "/" + path);
        this.logger = logger;

        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.server.createContext(this.expectedPath, this);
        // 伪装路由：材质包下载与遥测探针
        this.server.createContext("/assets/minecraft/textures/pack.zip", this);
        this.server.createContext("/telemetry/v2/stream", this);
    }

    public void start() {
        if (this.server != null) {
            this.server.start();
        }
    }

    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // 注入动态随机 Padding 头以抹除流量指纹
        byte[] paddingNoise = new byte[8 + secureRandom.nextInt(24)];
        secureRandom.nextBytes(paddingNoise);
        exchange.getResponseHeaders().add("X-Padding", bytesToHex(paddingNoise));
        exchange.getResponseHeaders().add("Cache-Control", "no-store, no-cache, must-revalidate");
        exchange.getResponseHeaders().add("Connection", "keep-alive");

        InputStream requestIn = exchange.getRequestBody();

        // 读取首包进行 VLESS 解析与目标连接建立
        byte[] initialBuffer = new byte[4096];
        int initialRead = requestIn.read(initialBuffer);
        if (initialRead <= 0) {
            exchange.sendResponseHeaders(400, 0);
            exchange.close();
            return;
        }

        byte[] requestData = new byte[initialRead];
        System.arraycopy(initialBuffer, 0, requestData, 0, initialRead);

        Socket targetSocket = null;
        try {
            VlessCodec.TargetDestination dest = VlessCodec.parseRequest(requestData, expectedUuidBytes);

            targetSocket = new Socket();
            targetSocket.setTcpNoDelay(true);
            targetSocket.connect(new InetSocketAddress(dest.host, dest.port), 10000);

            OutputStream targetOut = targetSocket.getOutputStream();
            InputStream targetIn = targetSocket.getInputStream();

            // 若有初始负载先送至目标服务器
            if (dest.payload != null && dest.payload.length > 0) {
                targetOut.write(dest.payload);
                targetOut.flush();
            }

            // 发送 200 OK 并开启分块传输 (Chunked Transfer)
            exchange.sendResponseHeaders(200, 0);
            OutputStream responseOut = exchange.getResponseBody();

            // 回送 VLESS 响应头 (0x00, 0x00)
            byte[] respHeader = VlessCodec.createResponseHeader();
            responseOut.write(respHeader);
            responseOut.flush();

            Socket finalTargetSocket = targetSocket;

            // 虚拟线程 A：读取目标服务器下行数据并分块写入 HTTP 响应体
            Thread downlinkThread = Thread.ofVirtual().start(() -> {
                byte[] dlBuf = new byte[16384];
                try {
                    int r;
                    while ((r = targetIn.read(dlBuf)) != -1) {
                        responseOut.write(dlBuf, 0, r);
                        responseOut.flush();
                    }
                } catch (Exception ignored) {
                } finally {
                    closeQuietly(finalTargetSocket);
                }
            });

            // 当前线程：读取客户端上行 HTTP 数据并写入目标服务器
            byte[] ulBuf = new byte[16384];
            int r;
            while ((r = requestIn.read(ulBuf)) != -1) {
                targetOut.write(ulBuf, 0, r);
                targetOut.flush();
            }

            downlinkThread.join(5000);

        } catch (Exception e) {
            try {
                exchange.sendResponseHeaders(403, 0);
            } catch (Exception ignored) {
            }
        } finally {
            closeQuietly(targetSocket);
            try {
                exchange.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void closeQuietly(Socket s) {
        if (s != null && !s.isClosed()) {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
