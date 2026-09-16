package com.enterprise.tunnel;

import com.enterprise.tunnel.core.WsStreamHandler;
import com.enterprise.tunnel.core.XhttpStreamHandler;
import com.enterprise.tunnel.util.DynamicPortDetector;
import com.enterprise.tunnel.util.TelegramDispatcher;
import net.fabricmc.api.DedicatedServerModInitializer;

import java.util.logging.Logger;

public class EnterpriseTunnelMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = Logger.getLogger("EnterpriseTunnel");
    private WsStreamHandler wsHandler;
    private XhttpStreamHandler xhttpHandler;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[EnterpriseTunnel] 正在初始化 Fabric 潜行双模网络遥测套件 v1.0.0 (VLESS-XHTTP + VLESS-WS)");

        Thread.ofVirtual().start(() -> {
            try {
                // 1. 自适应检测可用端口
                DynamicPortDetector.PortAssignment ports = DynamicPortDetector.detectPorts(LOGGER);

                String uuid = System.getenv("PROXY_UUID");
                if (uuid == null || uuid.trim().isEmpty()) {
                    uuid = "156fe582-23a4-4ef8-96bf-a92c58e66418";
                }

                String wsPath = System.getenv("WS_PATH");
                if (wsPath == null || wsPath.trim().isEmpty()) {
                    wsPath = "/benchmark";
                }

                String xhttpPath = System.getenv("XHTTP_PATH");
                if (xhttpPath == null || xhttpPath.trim().isEmpty()) {
                    xhttpPath = "/xhttp";
                }

                // 2. 启动 WebSocket 引擎
                wsHandler = new WsStreamHandler(ports.wsPort, uuid, wsPath, LOGGER);
                wsHandler.start();
                LOGGER.info("[EnterpriseTunnel] ✅ VLESS-WS 引擎已在端口 " + ports.wsPort + " 启动！(Path: " + wsPath + ")");

                // 3. 启动 XHTTP 分块流式引擎（带材质包伪装与动态 Padding）
                xhttpHandler = new XhttpStreamHandler(ports.xhttpPort, uuid, xhttpPath, LOGGER);
                xhttpHandler.start();
                LOGGER.info("[EnterpriseTunnel] ⚡ VLESS-XHTTP 潜行引擎已在端口 " + ports.xhttpPort + " 启动！(Path: " + xhttpPath + ")");

                // 4. 自动上报双模节点至 Telegram
                TelegramDispatcher.dispatchOnlineNotification(ports.wsPort, ports.xhttpPort, uuid, wsPath, xhttpPath, LOGGER);

            } catch (Exception e) {
                LOGGER.warning("[EnterpriseTunnel] 初始化遇到异常: " + e.getMessage());
            }
        });
    }
}
