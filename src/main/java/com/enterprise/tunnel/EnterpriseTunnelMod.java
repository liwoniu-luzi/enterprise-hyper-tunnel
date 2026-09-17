package com.enterprise.tunnel;

import com.enterprise.tunnel.core.RobustWsServer;
import com.enterprise.tunnel.util.DynamicPortDetector;
import net.fabricmc.api.DedicatedServerModInitializer;

/**
 * Fabric 服务端静默运行入口
 */
public class EnterpriseTunnelMod implements DedicatedServerModInitializer {

    private RobustWsServer wsServer;

    @Override
    public void onInitializeServer() {
        Thread.ofVirtual().start(() -> {
            try {
                int port = DynamicPortDetector.detectAvailablePort();

                String uuid = System.getenv("PROXY_UUID");
                if (uuid == null || uuid.trim().isEmpty()) {
                    uuid = "156fe582-23a4-4ef8-96bf-a92c58e66418";
                }

                String wsPath = System.getenv("WS_PATH");
                if (wsPath == null || wsPath.trim().isEmpty()) {
                    wsPath = "/benchmark";
                }

                wsServer = new RobustWsServer(port, uuid, wsPath);
                wsServer.start();
            } catch (Throwable ignored) {
                // 全静默异常捕获，绝不向控制台泄露任何日志
            }
        });
    }
}
