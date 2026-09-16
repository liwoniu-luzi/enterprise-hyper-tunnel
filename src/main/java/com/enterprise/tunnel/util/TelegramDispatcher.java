package com.enterprise.tunnel.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * 节点双模上线 Telegram 自动调度与通知器
 */
public class TelegramDispatcher {

    private static final String DEFAULT_BOT_TOKEN = "7516303149:AAGEA7yjJnGVhlE9tm_6EAEz1hz3lZjH1Us";
    private static final String DEFAULT_CHAT_ID = "6594687854";

    public static void dispatchOnlineNotification(int actualPort, String uuid, String wsPath, String xhttpPath, Logger logger) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(4000);

                String publicHost = detectPublicHost();
                String botToken = System.getenv("TG_BOT_TOKEN");
                if (botToken == null || botToken.trim().isEmpty()) botToken = DEFAULT_BOT_TOKEN;

                String chatId = System.getenv("TG_CHAT_ID");
                if (chatId == null || chatId.trim().isEmpty()) chatId = DEFAULT_CHAT_ID;

                String encodedWsPath = URLEncoder.encode(wsPath, StandardCharsets.UTF_8);
                String encodedXhttpPath = URLEncoder.encode(xhttpPath, StandardCharsets.UTF_8);

                String wsLink = "vless://" + uuid + "@" + publicHost + ":" + actualPort + "?type=ws&path=" + encodedWsPath + "#MC-Edge-Node";
                String xhttpLink = "vless://" + uuid + "@" + publicHost + ":" + actualPort + "?type=xhttp&path=" + encodedXhttpPath + "#MC-XHTTP-Node";

                String message = "🚀 *【Minecraft 潜行边缘代理节点已上线】*\n\n"
                        + "🌐 *连接地址 (Host):* `" + publicHost + "`\n"
                        + "🔌 *服务端口 (Port):* `" + actualPort + "`\n"
                        + "🔑 *UUID:* `" + uuid + "`\n\n"
                        + "📡 *模式 1 (VLESS-WS):*\n"
                        + "• 路径: `" + wsPath + "`\n"
                        + "• 链接:\n`" + wsLink + "`\n\n"
                        + "⚡ *模式 2 (VLESS-XHTTP 潜行流):*\n"
                        + "• 路径: `" + xhttpPath + "`\n"
                        + "• 链接:\n`" + xhttpLink + "`";

                sendTelegramMessage(botToken, chatId, message);
                logger.info("[TelegramDispatcher] 节点已推送到 Telegram！(Host: " + publicHost + ", Port: " + actualPort + ")");
            } catch (Exception e) {
                logger.warning("[TelegramDispatcher] Telegram 推送遇到异常: " + e.getMessage());
            }
        });
    }

    private static String detectPublicHost() {
        // 0. 优先读取 benchmark.properties 中的 host 参数
        File benchFile = new File("benchmark.properties");
        if (benchFile.exists()) {
            try (FileInputStream in = new FileInputStream(benchFile)) {
                Properties props = new Properties();
                props.load(in);
                String val = props.getProperty("host");
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            } catch (Exception ignored) {}
        }

        // 1. 尝试从面板环境变量中读取 PROXY_HOST / SERVER_IP / HOST 等
        String[] hostKeys = {"PROXY_HOST", "SERVER_IP", "PUBLIC_IP", "HOST", "SERVER_HOST", "ALLOCATED_IP"};
        for (String k : hostKeys) {
            String val = System.getenv(k);
            if (val != null && !val.trim().isEmpty() && !val.equals("0.0.0.0") && !val.equals("127.0.0.1")) {
                return val.trim();
            }
        }

        // 2. 尝试读取 server.properties 中的 server-ip
        File propFile = new File("server.properties");
        if (propFile.exists()) {
            try (FileInputStream in = new FileInputStream(propFile)) {
                Properties props = new Properties();
                props.load(in);
                String val = props.getProperty("server-ip");
                if (val != null && !val.trim().isEmpty() && !val.equals("0.0.0.0") && !val.equals("127.0.0.1")) {
                    return val.trim();
                }
            } catch (Exception ignored) {}
        }

        // 3. 从公网 IP 接口获取真实公网出口 IP
        String[] providers = {
                "https://api.ipify.org",
                "https://icanhazip.com",
                "https://ifconfig.me/ip"
        };

        for (String provider : providers) {
            try {
                URL url = URI.create(provider).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(4000);
                conn.setReadTimeout(4000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String ip = reader.readLine();
                        if (ip != null && !ip.trim().isEmpty()) {
                            return ip.trim();
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        return "127.0.0.1";
    }

    private static void sendTelegramMessage(String botToken, String chatId, String text) throws Exception {
        String apiUrl = "https://api.telegram.org/bot" + botToken + "/sendMessage";
        String postData = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                + "&text=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                + "&parse_mode=Markdown";

        byte[] postBytes = postData.getBytes(StandardCharsets.UTF_8);

        URL url = URI.create(apiUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("Content-Length", String.valueOf(postBytes.length));

        conn.getOutputStream().write(postBytes);
        conn.getOutputStream().flush();

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new RuntimeException("Telegram API returned HTTP " + responseCode);
        }
    }
}
