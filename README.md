# EnterpriseHyperTunnel (Minecraft 双模潜行网络遥测套件)

> **新一代企业级伪装与反风控代理架构**：全新独立重构，支持 **VLESS-XHTTP（流式分块+材质包伪装+动态Padding）** 与 **VLESS-WS** 双引擎并发，零外部二进制依赖，100% 纯 Java 21 原生编写。

---

## 🌟 核心特性与优势

1. **VLESS-XHTTP + VLESS-WS 双引擎并存**：
   - **XHTTP 模式**：基于 JDK 原生 `HttpServer` 与虚拟线程（Virtual Threads）实现流式分块传输，彻底抗审查、低延迟；
   - **WS 模式**：原生 WebSocket 高性能并发流。
2. **深度防风控与流量特征混淆**：
   - **材质包/遥测伪装**：内置路由 `/assets/minecraft/textures/pack.zip` 和 `/telemetry/v2/stream`，抓包呈现为标准服务端资源与遥测下载；
   - **动态随机 Padding**：在 HTTP 报头中注入随机长度 Padding 混淆字节，抹除数据包固定大小与 MTU 指纹。
3. **智能多源端口探测**：
   - 自动读取面板环境变量（`PROXY_PORT`、`WS_PORT`、`XHTTP_PORT`、`ALLOCATED_PORT` 等）；
   - 自动偏移 `server.properties` 端口并扫描 `20000~65535` 范围可用安全高位端口。
4. **双模 Telegram 自动上线通知**：
   - 服务端启动后，自动获取公网 IP 并将 WS 和 XHTTP 的 `vless://` 一键导入链接推送到 Telegram。
5. **全平台/全版本兼容**：
   - Fabric 服务端放入 `mods/` 即可运行（全版本通配 `minecraft: *`）。

---

## 🛠️ 项目结构

```text
enterprise-hyper-tunnel/
├── pom.xml                                    # Maven Shade 打包配置 (Java 21)
├── .github/workflows/build.yml                # GitHub Actions 自动化构建与 Release
├── README.md                                  # 项目文档
└── src
    └── main
        ├── java/com/enterprise/tunnel/
        │   ├── EnterpriseTunnelMod.java       # Mod 启动入口
        │   ├── core/
        │   │   ├── WsStreamHandler.java       # VLESS-WS 转发引擎
        │   │   ├── XhttpStreamHandler.java    # VLESS-XHTTP 分块流式引擎 (带动态 Padding)
        │   │   └── VlessCodec.java            # VLESS v0 协议编解码器
        │   └── util/
        │       ├── DynamicPortDetector.java   # 动态端口自适应探测器
        │       └── TelegramDispatcher.java    # 双模节点上线 TG 调度器
        └── resources/
            └── fabric.mod.json                # Fabric Mod 元信息配置
```
