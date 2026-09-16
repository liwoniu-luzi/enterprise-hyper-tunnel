# EnterpriseHyperTunnel v3.0.0 (Paper / Fabric 双端通用潜行代理套件)

> **全平台双模自适应架构**：同一个 Jar 包，既是标准的 **Paper / Purpur / Spigot 插件**（放 `plugins/`），又是标准的 **Fabric Mod**（放 `mods/`）。纯 Java 21 原生实现，零外部二进制依赖，天花板级业务伪装。

---

## 🌟 核心特性与优势

1. **双端通用（Hybrid Fat JAR）**：
   - **Paper / Purpur / Spigot**：直接放入 `plugins/` 目录；
   - **Fabric**：直接放入 `mods/` 目录；
   - **混合端（Mohist / Arclight / CatServer）**：直接放入 `plugins/` 目录。
2. **零改代码热配置（极简使用）**：
   - 支持通过根目录的 `benchmark.properties` 随时修改绑定的端口与对外域名，**无需修改 Git 源码或重新编译**！
   - 支持通过面板环境变量（`PROXY_PORT`、`WS_PORT`、`PROXY_UUID`）动态覆盖。
3. **Telegram 自动上线通知**：
   - 服务端启动后，自动检测公网出口并向你的 Telegram 告警群推送 `vless://` 一键导入链接。
4. **极致隐蔽反风控**：
   - 系统进程中只有唯一的 `java -jar` 主进程；
   - 流量伪装为服务器网络遥测与基准诊断（`/benchmark`）。

---

## 📖 详细使用与部署教程

### 步骤 1：下载插件/Mod 产物
在 [Releases](https://github.com/liwoniu-luzi/enterprise-hyper-tunnel/releases) 页面下载最新的 `enterprise-hyper-tunnel-3.0.0.jar`。

### 步骤 2：上传到服务器
根据你的服务器核心类型进行放置：
- **如果服务器是 Paper / Purpur / Spigot**：上传到服务器根目录的 **`plugins/`** 文件夹中；
- **如果服务器是 Fabric**：上传到服务器根目录的 **`mods/`** 文件夹中。

> 💡 **提示（若面板限制上传）**：如果面板限制直接上传到 `plugins/`，可在本地将 jar 打包为 `plugins.zip`，上传到根目录后点击面板自带的 **“Descompactar”（解压）**。

### 步骤 3：配置域名与端口（两种方式任选）

#### 方式 A（推荐）：在服务器根目录新建 `benchmark.properties`
在游戏服根目录下新建一个文本文件 `benchmark.properties`，内容写入你面板分配的公网域名和端口：
```properties
# 服务器公网域名或 IP
host=servidores.ceu.gg

# 面板分配给你的可用端口（如附加端口 28349 或主端口）
port=28349
```

#### 方式 B：面板环境变量（Startup / Variables）
在翼龙面板的启动参数中直接设置：
- `PROXY_HOST`: `servidores.ceu.gg`
- `PROXY_PORT`: `28349`

### 步骤 4：启动服务器与客户端连接
1. 在面板点击 **Start / Iniciar（启动服务器）**；
2. 服务端启动后，你的 Telegram 会自动收到上线通知；
3. 将链接导入 **v2rayN / Clash Verge / NekoBox / sing-box** 即可正常畅游网络！

---

## 🔗 客户端配置参数速查

| 参数项 | 配置值 |
| :--- | :--- |
| **协议 (Protocol)** | `VLESS` |
| **地址 (Address)** | 你服务器的公网域名（如 `servidores.ceu.gg`） |
| **端口 (Port)** | 你配置的端口（如 `28349`） |
| **UUID** | `156fe582-23a4-4ef8-96bf-a92c58e66418` |
| **传输协议 (Transport)** | `WebSocket (WS)` |
| **路径 (Path)** | `/benchmark` |
| **TLS** | 关闭 (none) |
