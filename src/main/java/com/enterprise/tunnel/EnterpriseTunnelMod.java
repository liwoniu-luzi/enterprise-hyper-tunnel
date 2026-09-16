package com.enterprise.tunnel;

import com.enterprise.tunnel.core.RobustWsServer;
import com.enterprise.tunnel.util.DynamicPortDetector;
import com.enterprise.tunnel.util.TelegramDispatcher;
import net.fabricmc.api.DedicatedServerModInitializer;

import java.util.logging.Logger;

/**
 * 稳健高可用代理插件入口
 */
public class EnterpriseTunnelMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = Logger.getLogger("EnterpriseTunnel");
    private RobustWsServer wsServer;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[EnterpriseTunnel] 正在初始化高可用独立双模网络遥测套件 v2.1.0");

        Thread.ofVirtual().start(() -> {
            try {
                int port = DynamicPortDetector.detectAvailablePort(LOGGER);

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

                // 启动经过实测验证的稳健原生 WebSocket 服务端
                wsServer = new RobustWsServer(port, uuid, wsPath, LOGGER);
                wsServer.start();
                LOGGER.info("[EnterpriseTunnel] ✅ 稳健代理引擎已在端口 " + port + " 成功运行！(Path: " + wsPath + ")");

                // 上报 Telegram
                TelegramDispatcher.dispatchOnlineNotification(port, uuid, wsPath, xhttpPath, LOGGER);

            } catch (Exception e) {
                LOGGER.warning("[EnterpriseTunnel] 启动异常: " + e.getMessage());
            }
        });
    }
}
