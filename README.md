# ten4j
![Demo](https://github.com/hanger-source/ten-realtime-chat/raw/main/demo.png)

- [返回主项目](https://github.com/hanger-source/ten-realtime-chat): `ten-realtime-chat` 是一个全面的实时聊天应用示例，旨在展示如何高效整合基于 WebSocket 的前端和基于 Java `ten4j` 框架的后端，实现高性能、高并发的双向实时通信。

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://github.com/hanger-source/ten4j/blob/main/LICENSE)

## 🚀 项目简介

`[ten4j](https://github.com/hanger-source/ten4j)` 是 [`TEN-framework`](https://github.com/TEN-framework/ten-framework) 的一个 Java 参考实现，旨在为开发者提供一个基于 Java 的、用于构建会话式 AI 代理的强大后端框架。本项目实现了 `TEN-framework` 的部分核心功能，专注于后端逻辑、实时通信（通过 WebSocket）以及与 AI 服务的集成，为开发高性能、高并发的实时交互应用奠定基础。

**⚠️ 注意**: 本项目目前仅保证核心功能可正常运行。部分功能代码可能仍存在错误或未经过全面测试。

## ✨ 核心特性

-   **WebSocket 实时通信**: 提供稳定高效的 WebSocket 连接管理，支持双向数据流，实现客户端与服务器之间的实时交互。
-   **高性能事件循环 (Runloop)**: 核心 `Runloop` 基于 Agrona `AgentRunner` 实现，提供高效的任务调度和线程管理，确保系统响应性和吞吐量。
-   **可插拔扩展机制 (Extension)**: 通过灵活的扩展机制，轻松集成外部 AI 服务（如 ASR、TTS、LLM）和其他系统组件，目前已支持部分阿里云 Bailian 平台的 ASR、TTS 和 LLM 集成。
-   **命令处理机制**: 实现了灵活的命令接收和分发机制，能够处理来自前端或外部系统的各类指令。
-   **AI 服务集成接口**: 预留了与 ASR (自动语音识别)、TTS (文本转语音) 和 LLM (大型语言模型) 等 AI 服务集成的能力，方便构建多模态 AI 应用。
-   **模块化设计**: 采用清晰的模块划分（`ten4j-agent`, `ten4j-core`, `ten4j-server`），易于理解、维护和功能扩展。
-   **图（Graph）运行时**: 支持基于图（Graph）的会话流程定义和执行，提供灵活的业务逻辑编排能力。

## 🛠️ 技术栈

-   **核心框架**: Java
-   **构建工具**: [Maven](https://maven.apache.org/)
-   **网络通信**: [Netty](https://netty.io/) (用于 WebSocket 服务器)
-   **协议**: WebSocket
-   **日志**: SLF4J / Logback
-   **JSON 处理**: Jackson

## 📦 模块结构

`ten4j` 项目由以下几个主要模块组成：

-   **`ten4j-core`**: 包含项目的核心业务逻辑、命令处理、消息定义、协议解析以及 AI 服务接口等基础组件。它是 `ten4j` 的基石。
-   **`ten4j-server`**: 提供 WebSocket 服务器的实现，负责管理客户端连接、处理 WebSocket 帧，并将数据转发给 `ten4j-core` 进行处理。它是前端应用连接的入口。
-   **`ten4j-agent`**: 包含与特定 AI 代理相关的实现细节，例如具体的 ASR、TTS、LLM 客户端实现等。此模块通常用于集成和演示特定的 AI 能力。

## ⚙️ 如何运行

要运行 `ten4j` 项目，您需要安装 Java 开发环境 (JDK 21 或更高版本) 和 Apache Maven。

### 1. 克隆项目

首先，克隆 `ten-realtime-chat` 仓库到本地：

```bash
git clone <项目地址>
cd ten-realtime-chat
```

### 2. 构建并运行 `ten4j`

进入 `ten4j` 目录，然后使用 Maven 构建并运行项目：

```bash
cd ten4j
mvn clean install -Pbuild-jar
java -Dserver.port=8080 --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -jar ten4j-server/target/ten4j-server-1.0-SNAPSHOT.jar
```

您可以通过 `-Dserver.port=<端口号>` 参数来指定后端服务的端口，例如 `-Dserver.port=8080`。

此命令会编译所有模块并启动 `ten4j-server`，它将监听 WebSocket 连接。请注意控制台输出，确认服务已成功启动。

## 🔗 与 TEN-framework 的关系

`[ten4j](https://github.com/hanger-source/ten4j)` 是 `TEN-framework` (一个开源的会话式 AI 代理框架) 的一个 Java 实现。`TEN-framework` 旨在提供构建多模态、实时 AI 代理的通用能力，而 `TEN-framework` 则是利用 Java 语言和生态系统，对其中部分核心概念和功能进行了具体实现和探索。您可以访问 `TEN-framework` 的官方 GitHub 仓库了解更多信息：

-   **TEN-framework GitHub**: [https://github.com/TEN-framework/ten-framework](https://github.com/TEN-framework/ten-framework)

## 📄 许可证

本项目采用 Apache 2.0 许可证。详情请参阅项目根目录下的 [`LICENSE`](https://github.com/hanger-source/ten4j/blob/main/LICENSE) 文件。