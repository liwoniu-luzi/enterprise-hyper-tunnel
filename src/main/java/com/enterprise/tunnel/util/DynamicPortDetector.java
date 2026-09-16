package com.enterprise.tunnel.util;

import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 稳健多源端口探测器
 */
public class DynamicPortDetector {

    private static final int DEFAULT_PORT = 14894;
    private static final int MIN_SEARCH_PORT = 20000;
    private static final int MAX_SEARCH_PORT = 65535;

    public static int detectAvailablePort(Logger logger) {
        // 1. 检查环境变量
        String[] envKeys = {"PROXY_PORT", "WS_PORT", "ALLOCATED_PORT", "SERVER_PORT_2", "PORT_2", "SERVER_PORT", "PORT"};
        for (String key : envKeys) {
            String val = System.getenv(key);
            if (val != null && !val.trim().isEmpty()) {
                try {
                    int p = Integer.parseInt(val.trim());
                    if (isPortAvailable(p)) {
                        logger.info("[DynamicPortDetector] 从环境变量 " + key + " 获取并验证可用端口: " + p);
                        return p;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // 2. 检查 benchmark.properties
        File benchFile = new File("benchmark.properties");
        if (benchFile.exists()) {
            try (FileInputStream in = new FileInputStream(benchFile)) {
                Properties props = new Properties();
                props.load(in);
                String pStr = props.getProperty("port");
                if (pStr != null) {
                    int p = Integer.parseInt(pStr.trim());
                    if (isPortAvailable(p)) return p;
                }
            } catch (Exception ignored) {}
        }

        // 3. 检查 server.properties
        File propFile = new File("server.properties");
        if (propFile.exists()) {
            try (FileInputStream in = new FileInputStream(propFile)) {
                Properties props = new Properties();
                props.load(in);
                String pStr = props.getProperty("server-port");
                if (pStr != null) {
                    int sp = Integer.parseInt(pStr.trim());
                    int cand = sp + 1;
                    if (cand <= MAX_SEARCH_PORT && isPortAvailable(cand)) {
                        logger.info("[DynamicPortDetector] 基于 server-port 自动偏移获取端口: " + cand);
                        return cand;
                    }
                }
            } catch (Exception ignored) {}
        }

        // 4. 默认端口 14894
        if (isPortAvailable(DEFAULT_PORT)) {
            logger.info("[DynamicPortDetector] 使用专属默认端口: " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }

        // 5. 动态高位端口扫描
        for (int p = MIN_SEARCH_PORT; p <= MAX_SEARCH_PORT; p++) {
            if (isPortAvailable(p)) {
                logger.info("[DynamicPortDetector] 自动扫描分配到高位空闲端口: " + p);
                return p;
            }
        }

        return DEFAULT_PORT;
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
}
