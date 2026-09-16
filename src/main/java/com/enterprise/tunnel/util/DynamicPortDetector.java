package com.enterprise.tunnel.util;

import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 智能端口适配器（支持多场景自适应）
 */
public class DynamicPortDetector {

    public static int detectBestPort(Logger logger) {
        // 1. 优先读取面板环境变量
        String[] keys = {"SERVER_PORT", "PORT", "ALLOCATED_PORT", "PROXY_PORT", "WS_PORT"};
        for (String k : keys) {
            String val = System.getenv(k);
            if (val != null && !val.trim().isEmpty()) {
                try {
                    int p = Integer.parseInt(val.trim());
                    logger.info("[DynamicPortDetector] 从环境变量 " + k + " 获取到服务器对外端口: " + p);
                    return p;
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. 其次从 server.properties 中读取
        File propFile = new File("server.properties");
        if (propFile.exists()) {
            try (FileInputStream in = new FileInputStream(propFile)) {
                Properties props = new Properties();
                props.load(in);
                String val = props.getProperty("server-port");
                if (val != null) {
                    int p = Integer.parseInt(val.trim());
                    logger.info("[DynamicPortDetector] 从 server.properties 获取到 server-port: " + p);
                    return p;
                }
            } catch (Exception ignored) {}
        }

        return 25565;
    }
}
