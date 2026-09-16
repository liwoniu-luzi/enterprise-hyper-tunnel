package com.enterprise.tunnel;

import com.enterprise.tunnel.core.NettyPipelineInjector;
import com.enterprise.tunnel.util.DynamicPortDetector;
import com.enterprise.tunnel.util.TelegramDispatcher;
import net.fabricmc.api.DedicatedServerModInitializer;

import java.util.logging.Logger;

/**
 * Fabric 服务端入口点（零配置、Netty 端口复用架构）
 */
public class EnterpriseTunnelMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = Logger.getLogger("EnterpriseTunnel");

    @Override
    public void onInitializeServer() {
        LOGGER.info("[EnterpriseTunnel] 正在初始化 Fabric 全通用 Netty 端口复用潜行套件 v2.0.0");

        Thread.ofVirtual().start(() -> {
            try {
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

                // 1. 获取服务器正在监听的实际主端口（零额外端口占用）
                int actualPort = DynamicPortDetector.detectBestPort(LOGGER);

                // 2. 注入 Netty 管道实现协议嗅探分流
                NettyPipelineInjector.injectPipeline(uuid, wsPath, xhttpPath);

                // 3. 异步上报 Telegram
                TelegramDispatcher.dispatchOnlineNotification(actualPort, uuid, wsPath, xhttpPath, LOGGER);

            } catch (Exception e) {
                LOGGER.warning("[EnterpriseTunnel] 初始化遇到异常: " + e.getMessage());
            }
        });
    }
}
