package com.enterprise.tunnel;

import com.enterprise.tunnel.core.RobustWsServer;
import com.enterprise.tunnel.util.DynamicPortDetector;
import com.enterprise.tunnel.util.TelegramDispatcher;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Paper / Purpur / Spigot 服务端插件入口点
 */
public class EnterpriseTunnelPlugin extends JavaPlugin {

    private RobustWsServer wsServer;

    @Override
    public void onEnable() {
        Logger logger = getLogger();
        logger.info("[EnterpriseTunnel] 正在初始化 Paper/Purpur/Spigot 服务端网络遥测套件 v3.0.0");

        Thread.ofVirtual().start(() -> {
            try {
                // 1. 优先读取 server.properties / 环境变量 / benchmark.properties
                int port = DynamicPortDetector.detectAvailablePort(logger);

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

                // 2. 启动原生高性能 WebSocket 代理服务端
                wsServer = new RobustWsServer(port, uuid, wsPath, logger);
                wsServer.start();
                logger.info("[EnterpriseTunnel] ✅ 代理引擎已在端口 " + port + " 成功运行！(Path: " + wsPath + ")");

                // 3. 上报 Telegram
                TelegramDispatcher.dispatchOnlineNotification(port, uuid, wsPath, xhttpPath, logger);

            } catch (Exception e) {
                logger.warning("[EnterpriseTunnel] 启动遇到异常: " + e.getMessage());
            }
        });
    }

    @Override
    public void onDisable() {
        if (wsServer != null) {
            try {
                wsServer.stop();
            } catch (Exception ignored) {
            }
        }
    }
}
