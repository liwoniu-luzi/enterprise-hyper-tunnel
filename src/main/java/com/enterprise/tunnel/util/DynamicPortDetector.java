package com.enterprise.tunnel.util;

import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 动态多源端口探测与安全性检查器
 */
public class DynamicPortDetector {

    private static final int DEFAULT_WS_PORT = 14894;
    private static final int DEFAULT_XHTTP_PORT = 14895;
    private static final int MIN_SAFE_PORT = 20000;
    private static final int MAX_SAFE_PORT = 65535;

    public static class PortAssignment {
        public int wsPort;
        public int xhttpPort;
    }

    public static PortAssignment detectPorts(Logger logger) {
        PortAssignment assignment = new PortAssignment();

        // 1. 探测 WS 端口
        String[] wsEnvKeys = {"WS_PORT", "PROXY_PORT", "ALLOCATED_PORT", "PORT_2", "SERVER_PORT_2", "PORT"};
        int ws = findPortFromEnv(wsEnvKeys);
        if (ws > 0 && isPortAvailable(ws)) {
            assignment.wsPort = ws;
            logger.info("[DynamicPortDetector] 成功从环境变量获取 WS 可用端口: " + ws);
        } else {
            assignment.wsPort = findFallbackPort(DEFAULT_WS_PORT, -1);
            logger.info("[DynamicPortDetector] 自动分配 WS 端口: " + assignment.wsPort);
        }

        // 2. 探测 XHTTP 端口（如果分配了独立的 XHTTP_PORT 环境变量）
        String[] xhttpEnvKeys = {"XHTTP_PORT", "HTTP_PORT", "PORT_3", "SERVER_PORT_3"};
        int xhttp = findPortFromEnv(xhttpEnvKeys);
        if (xhttp > 0 && isPortAvailable(xhttp) && xhttp != assignment.wsPort) {
            assignment.xhttpPort = xhttp;
            logger.info("[DynamicPortDetector] 成功从环境变量获取 XHTTP 可用端口: " + xhttp);
        } else {
            assignment.xhttpPort = findFallbackPort(DEFAULT_XHTTP_PORT, assignment.wsPort);
            logger.info("[DynamicPortDetector] 自动分配 XHTTP 端口: " + assignment.xhttpPort);
        }

        return assignment;
    }

    private static int findPortFromEnv(String[] keys) {
        for (String key : keys) {
            String val = System.getenv(key);
            if (val != null && !val.trim().isEmpty()) {
                try {
                    int p = Integer.parseInt(val.trim());
                    if (isPortAvailable(p)) {
                        return p;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    private static int findFallbackPort(int preferred, int excludePort) {
        if (preferred != excludePort && isPortAvailable(preferred)) {
            return preferred;
        }

        // 尝试读取 server.properties 并计算偏移
        int serverPort = readServerPropertiesPort();
        if (serverPort > 0) {
            for (int offset = 1; offset <= 10; offset++) {
                int candidate = serverPort + offset;
                if (candidate != excludePort && candidate <= MAX_SAFE_PORT && isPortAvailable(candidate)) {
                    return candidate;
                }
            }
        }

        // 动态范围扫描
        for (int p = MIN_SAFE_PORT; p <= MAX_SAFE_PORT; p++) {
            if (p != excludePort && isPortAvailable(p)) {
                return p;
            }
        }

        return preferred;
    }

    public static boolean isPortAvailable(int port) {
        if (port < 1024 || port > 65535) return false;
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static int readServerPropertiesPort() {
        File file = new File("server.properties");
        if (!file.exists() || !file.isFile()) return -1;
        try (FileInputStream in = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(in);
            String val = props.getProperty("server-port");
            if (val != null) {
                return Integer.parseInt(val.trim());
            }
        } catch (Exception ignored) {
        }
        return -1;
    }
}
