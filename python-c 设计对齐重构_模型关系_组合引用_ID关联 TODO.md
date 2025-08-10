# ten-framework Java 核心模型重构 TODO 列表

## 概述

本 TODO 列表旨在指导 ten-framework Java 核心模型的重构，使其在模型关系、构建方式、属性维护、依赖组合以及 ID 关联等方面与 C/Python 底层源码保持高度一致。这并非一蹴而就的任务，我们将通过多阶段、多层次的拆分，逐步构建一个可运行的开发环境骨架。

## ai/output 直接在这个项目上做修改 重构

请开始 先检查是否有相似的类 如果有则修改调整即可 （但是保证符合新的重构思路） 没有则新建 避免同时出现类似的类 老的在新的也在 导致代码混乱
！！！请认真对待这句话！！！

重构的重点在于：

1.  **模型间的职责与所有权**：明确每个类的核心职责及其对其他实例的所有权/引用关系。
2.  **构建与生命周期**：理解实例的创建、初始化和销毁流程。
3.  **属性与 ID 关联**：识别并正确使用关键的唯一标识符（ID）来维护模型间的联系。
4.  **依赖与组合**：区分强依赖（组合）和弱依赖（引用），合理设计类之间的关系。
5.  **线程模型对齐**：尤其关注 App、Engine、Protocol 之间线程上下文的流转。

## 阶段一：基础数据结构和通信原语

### 子阶段 1.1：核心枚举与 Location 模型

- **目标：** 定义框架内通用的枚举类型，并精确实现 `Location` 模型，作为消息路由的基础。
- **设计细节：** `Location` 是 `Message` 路由的核心寻址机制，其属性直接映射 `ten_loc_t`。
  1.  **TODO: `ConnectionMigrationState.java`**
      - **构建：** 定义 Java `enum`。
      - **属性：** `INITIAL`, `FIRST_MSG`, `MIGRATING`, `MIGRATED`, `CLEANING`, `CLEANED`。
      - **关联：** `Connection` 实例通过此枚举维护其生命周期中的线程迁移状态。

  2.  **TODO: `MessageType.java`**
      - **构建：** 定义 Java `enum`。
      - **属性：** `CMD_START_GRAPH`, `CMD_STOP_GRAPH`, `CMD_CLOSE_APP`, `CMD_RESULT`, `DATA_MESSAGE` 等。
      - **关联：** `Message` 实例通过此枚举区分其类型和用途。

  3.  **TODO: `Location.java` (对应 `core/include_internal/ten_runtime/common/loc.h` 的 `ten_loc_t`)**
      - **构建：** 创建 `Location` 类，包含 `appUri`, `graphId`, `nodeId` 属性。
      - **属性：**
        - `String appUri`：对应 `ten_string_t app_uri`，标识 `App` 实例的唯一 URI。
        - `String graphId`：对应 `ten_string_t graph_id`，标识 `Engine` 实例的唯一 ID，也是 `Graph` 实例的运行时 ID。
        - `String nodeId`：对应 `ten_string_t node_id`，可选，标识 `Extension` 实例的唯一 ID。
      - **依赖：** 无直接依赖，但被 `Message` 组合。
      - **组合：** 被 `Message` 组合，构成消息的源和目的地址。
      - **ID 关联：** `appUri` 关联 `App.appUri`，`graphId` 关联 `Engine.graphId`，`nodeId` 关联 `Extension.id`。
      - **实现：** 包含构造函数、Getter 方法，以及正确的 `equals()` 和 `hashCode()` 实现，以支持在 `Map` 中作为 Key 使用。

### 子阶段 1.2：消息模型

- **目标：** 实现 `Message` 基类及其派生类，作为框架内所有通信的数据载体。
- **设计细节：** 消息是框架内流转的基本单位，包含路由所需的所有关键 ID。`Command` 是一种特殊 `Message`。
  1.  **TODO: `Message.java` (对应 `core/include_internal/ten_runtime/msg/msg.h` 的 `ten_shared_ptr_t` 封装的 `msg`)**
      - **构建：** 创建 `Message` 基类。
      - **属性：**
        - `String messageId`：消息的唯一 ID (UUID)。
        - `MessageType type`：消息类型。
        - `Location srcLoc`：消息的来源地址，**组合** `Location` 实例。
        - `Location destLoc`：消息的目的地址，**组合** `Location` 实例。
        - `String payload`：消息内容（简化为字符串，实际可能是 `byte[]` 或序列化对象）。
      - **组合：** **组合** `Location` 实例作为源和目的地址。
      - **ID 关联：** `srcLoc` 和 `destLoc` 中的 `appUri`, `graphId`, `nodeId` 是路由和寻址的关键。

  2.  **TODO: `StartGraphCommand.java` (派生自 `Message`)**
      - **构建：** 继承 `Message`。
      - **属性：**
        - `String graphJsonDefinition`：图的 JSON 定义字符串。
        - `boolean longRunningMode`：对应 `Engine` 的长运行模式。
      - **关联：** 在 `App` 接收并处理 `start_graph` 命令时，这些属性用于初始化 `Engine`。

  3.  **TODO: `CommandResult.java` (派生自 `Message`)**
      - **构建：** 继承 `Message`。
      - **属性：**
        - `int statusCode`：命令执行结果状态码。
        - `String detail`：详细信息。
        - `String originalCommandId`：引用原始命令的 `messageId`。
      - **关联：** 用于将命令执行结果从 `Engine` 回传给 `App` 或发起 `Connection` 的客户端。

### 子阶段 1.3：协议接口和基础实现

- **目标：** 定义 `Protocol` 接口，并提供一个基础的实现，模拟底层网络通信的协议转换层。
- **设计细节：** `Protocol` 负责 `Connection` 的序列化/反序列化。它可以在独立的线程池中运行，模拟 C 中的 `External protocol thread`。
  1.  **TODO: `Protocol.java` (接口 - 对应 `core/include_internal/ten_runtime/protocol/protocol.h`)**
      - **构建：** 定义 `Protocol` 接口。
      - **方法：**
        - `String getName()`：获取协议名称。
        - `Message parse(byte[] data)`：将字节流解析为 `Message`。
        - `byte[] serialize(Message message)`：将 `Message` 序列化为字节流。
        - `void onConnectionMigrated(Connection connection)`：通知协议层连接已迁移到 `Engine`。
        - `void onConnectionCleaned(Connection connection)`：通知协议层连接已清理。
        - `void startIoHandler()`：启动 I/O 处理。
        - `void stopIoHandler()`：停止 I/O 处理。
      - **依赖：** 依赖 `Message`。

  2.  **TODO: `WebSocketProtocol.java` (实现类 - 示例)**
      - **构建：** 实现 `Protocol` 接口。
      - **属性：** 内部可以有一个 `ExecutorService ioExecutor`，模拟 `External protocol thread`，用于处理 I/O 任务。
      - **实现：** 模拟 `parse` 和 `serialize` 方法，以及 `onConnectionMigrated`, `onConnectionCleaned` 等回调。
      - **依赖：** 组合 `ExecutorService`。

## 阶段二：连接和 Engine 生命周期

### 子阶段 2.1：Connection 模型

- **目标：** 实现 `Connection` 模型，精确反映其与 `App`、`Engine` 和 `Remote` 的绑定关系及迁移状态。
- **设计细节：** `Connection` 是物理连接的抽象，其 `attachedTarget` 动态变化，反映所有权转移。
  1.  **TODO: `Connection.java` (对应 `core/include_internal/ten_runtime/connection/connection.h` 的 `ten_connection_t`)**
      - **构建：** 创建 `Connection` 类。
      - **属性：**
        - `String connectionId`：连接的唯一 ID (UUID，模拟 `ten_string_t uri`)。
        - `Object attachedTarget`：**引用**当前连接所绑定的实体（可以是 `App`、`Engine` 或 `Remote` 实例）。
        - `ConnectionMigrationState migrationState`：连接的迁移状态，**组合** `ConnectionMigrationState` 枚举。
        - `Protocol protocol`：**组合**负责该连接底层通信的 `Protocol` 实例。
      - **组合：** 组合 `Protocol` 实例。
      - **依赖：** 依赖 `App`、`Engine`、`Remote`（通过 `attachedTarget` 引用）。
      - **方法：**
        - `attachToApp(App app)`：将 `Connection` 绑定到 `App`。
        - `attachToEngine(Engine engine)`：将 `Connection` 绑定到 `Engine`，触发 `Protocol.onConnectionMigrated`。
        - `attachToRemote(Remote remote)`：将 `Connection` 绑定到 `Remote`。
        - `sendData(byte[] data)`：通过 `Protocol` 发送数据。
        - `receiveData(): byte[]`：通过 `Protocol` 接收数据。
      - **ID 关联：** `connectionId` 作为唯一 ID。

### 子阶段 2.2：Engine 模型 - 核心属性和内部结构

- **目标：** 构建 `Engine` 类的核心结构，包括其 ID、所属关系、内部管理的对象以及消息处理机制。
- **设计细节：** `Engine` 是 `Graph` 的运行时实例，拥有独立的执行线程（可选），并管理 `PathTable`、`Extension`、`Remote` 和 `Connection`。
  1.  **TODO: `Engine.java` (对应 `core/include_internal/ten_runtime/engine/engine.h` 的 `ten_engine_t`)**
      - **构建：** 创建 `Engine` 类，作为 `EngineRuntimeEnv` 的实现。
      - **属性：**
        - `App ownerApp`：**引用**其所属的 `App` 实例。
        - `String graphId`：**唯一 ID**，对应 `ten_string_t graph_id`，标识 `Engine` 自身，也是其承载的 `Graph` 实例的运行时 ID。
        - `String graphName`：对应 `ten_string_t graph_name`。
        - `PathTable pathTable`：**组合** `PathTable` 实例，负责消息路由。
        - `Map<String, ExtensionInterface> extensions`：**组合**并管理 `Extension` 实例，Key 为 `Extension ID`。
        - `Map<String, Remote> remotes`：**组合**并管理 `Remote` 实例，Key 为 `Remote Target ID` (如 `targetAppUri/targetGraphId`)。
        - `List<Connection> orphanConnections`：**组合**并管理已绑定但未被 `Remote` 认领的 `Connection` 实例。
        - `Queue<Message> inMsgsQueue`：**组合** `Engine` 的输入消息队列。
        - `ExecutorService engineExecutor`：**组合** `Engine` 的专用线程池（单线程），模拟 `Engine thread`。
        - `boolean isReadyToHandleMsg`：标识 `Engine` 是否准备好处理消息。
        - `boolean longRunningMode`：对应 `start_graph` 命令中的长运行模式。
        - `Message originalStartGraphCommand`：**引用**启动该 `Engine` 的原始 `start_graph` `Command`。
      - **组合：** `PathTable`, `extensions` (通过 `Map` 组合 `Extension` 实例), `remotes` (通过 `Map` 组合 `Remote` 实例), `orphanConnections` (通过 `List` 组合 `Connection` 实例), `inMsgsQueue`, `ExecutorService`。
      - **依赖：** 依赖 `App` (引用), `StartGraphCommand` (用于构建), `Message`, `ExtensionInterface`, `Remote`, `Connection`。
      - **方法：**
        - 构造函数：接收 `App` 和 `StartGraphCommand`。在构造时根据 `Command` 和 `App` 配置生成 `graphId`，初始化内部组件，并启动 `Engine` 的消息处理循环。
        - `appendToInMsgsQueue(Message message)`：将消息添加到输入队列。
        - `startMessageProcessingLoop()`：启动 `ExecutorService` 上的消息处理任务，从 `inMsgsQueue` 取出消息并调用 `pathTable.routeMessage()`。
        - `loadExtensionsFromGraphDefinition(String graphJson)`：解析 `Graph JSON`，创建并初始化 `Extension` 实例，并配置 `PathTable` 路由。
        - `handleUnroutableMessage(Message message)`：处理无法路由的消息，例如发送错误结果。
        - `addOrphanConnection(Connection connection)`：将 `Connection` 添加到 `orphanConnections` 列表。
        - `findOrphanConnectionById(String connId)`：根据 `ID` 查找 `Connection`。
        - `getOrCreateRemote(String targetAppUri, String targetGraphId, Connection initialConnection)`：获取或创建 `Remote` 实例。
        - `shutdown()`：关闭 `Engine`，停止线程池，清理资源。
      - **ID 关联：** `graphId` 是 `Engine` 的核心 ID。`extensions` 和 `remotes` 均以 `ID` 作为 Key 维护。

### 子阶段 2.3：App 模型 - Engine 管理

- **目标：** 实现 `App` 的核心功能，包括 Engine 的创建、管理，以及顶层消息的接收和分发。
- **设计细节：** `App` 维护 `Engine` 实例的 Map，并控制 `Engine` 的线程模型。
  1.  **TODO: `App.java` (对应 `core/include_internal/ten_runtime/app/app.h` 的 `ten_app_t`)**
      - **构建：** 创建 `App` 类。
      - **属性：**
        - `String appUri`：**唯一 ID**，对应 `ten_string_t uri`。
        - `Map<String, Engine> engines`：**组合**并管理 `Engine` 实例，Key 为 `Engine` 的 `graphId`。
        - `Queue<Message> inMsgsQueue`：**组合** `App` 自身的输入消息队列，用于内部或跨 `App` 消息。
        - `ExecutorService appExecutor`：**组合** `App` 的专用线程池（单线程），模拟 `App thread`。
        - `boolean oneEventLoopPerEngine`：配置项，决定 `Engine` 是否有自己的事件循环。
      - **组合：** `engines` (通过 `Map` 组合 `Engine` 实例), `inMsgsQueue`, `ExecutorService`。
      - **依赖：** 依赖 `Connection`, `Message`, `Engine`。
      - **方法：**
        - 构造函数：初始化 `appUri` 和配置，启动 `App` 的消息处理循环。
        - `handleIncomingConnectionMessage(Connection connection, Message message)`：处理来自 `Connection` 的入站 `Message`，根据 `start_graph` 决定创建或查找 `Engine`，并进行 `Connection` 迁移。
        - `startAppMessageProcessingLoop()`：启动 `App` 的 `ExecutorService` 上的消息处理任务。
        - `receiveMessageFromEngine(Message message)`：接收来自 `Engine` 的消息（如 `CommandResult`）。
        - `removeOrphanedConnection(Connection connection)`：当 `Connection` 关闭时，从 `App` 层移除相关引用（如果仍由 `App` 维护）。
        - `shutdown()`：关闭 `App`，逐个关闭所有 `Engine`。
      - **ID 关联：** `appUri` 是 `App` 的核心 ID。`engines` 以 `graphId` 作为 Key。

## 阶段三：图的定义与加载

### 子阶段 3.1：Graph 定义模型

- **目标：** 建立 `Graph` 的 Java 模型，用于描述图的静态结构。
- **设计细节：** `GraphDefinition` 是静态蓝图，不直接参与运行时，但其内容用于构建 `Engine` 内部的 `PathTable` 和 `Extension`。
  1.  **TODO: `GraphDefinition.java`**
      - **构建：** 创建 `GraphDefinition` 类。
      - **属性：**
        - `String name`：图的名称。
        - `String jsonContent`：原始的 JSON 定义字符串。
        - `List<ExtensionDefinition> extensionDefinitions`：解析自 JSON 的 `Extension` 定义列表。
        - `List<PathDefinition> pathDefinitions`：解析自 JSON 的 `Path` 定义列表。
      - **依赖：** `ExtensionDefinition`, `PathDefinition` (待定义)。
      - **方法：** `parseJson(String json)`：解析 JSON 内容，填充 `extensionDefinitions` 和 `pathDefinitions`。
      - **关联：** `StartGraphCommand` 引用 `GraphDefinition` 的 JSON 内容。`Engine` 在构建时会解析 `GraphDefinition` 来初始化 `PathTable` 和 `Extension`。

  2.  **TODO: `ExtensionDefinition.java` (辅助类)**
      - **构建：** 描述 `Extension` 的静态属性（例如类型、配置）。

  3.  **TODO: `PathDefinition.java` (辅助类)**
      - **构建：** 描述 `Path` 的静态属性（例如源节点、目的节点、消息类型）。

### 子阶段 3.2：Extension 接口和基础实现

- **目标：** 定义 `Extension` 接口和运行时环境接口，并提供一个基础实现。
- **设计细节：** `Extension` 是 `Engine` 内部的业务处理单元，通过 `EngineRuntimeEnv` 与 `Engine` 交互。
  1.  **TODO: `ExtensionInterface.java` (接口 - 对应 `core/include_internal/ten_runtime/extension/extension.h`)**
      - **构建：** 定义 `ExtensionInterface` 接口。
      - **方法：** `getId()`, `initialize(EngineRuntimeEnv env)`, `onMessage(Message message)`, `shutdown()`.
      - **依赖：** 依赖 `EngineRuntimeEnv`。

  2.  **TODO: `EngineRuntimeEnv.java` (接口 - 对应 `core/include_internal/ten_runtime/ten_env/ten_env.h` 的 `ten_env_t`)**
      - **构建：** 定义 `EngineRuntimeEnv` 接口。
      - **方法：** `getEngineId()`, `sendMessageToEngine(Message message)`, `log(String level, String message)` 等。
      - **关联：** `Engine` 实现此接口，并将其自身实例传递给 `Extension`。

  3.  **TODO: `MyExtension.java` (实现类 - 示例)**
      - **构建：** 实现 `ExtensionInterface`，并在 `initialize` 中获取 `EngineRuntimeEnv`。
      - **实现：** 模拟 `onMessage` 处理，并通过 `env.sendMessageToEngine()` 发送结果。
      - **ID 关联：** `getId()` 返回 `Extension` 的唯一 ID，用于 `PathTable` 路由。

### 子阶段 3.3：PathTable 路由机制

- **目标：** 实现 `PathTable`，将 `Graph` 定义中的路由逻辑具象化为运行时可用的消息分发机制。
- **设计细节：** `PathTable` 属于 `Engine`，其路由规则基于 `Location` 决定消息走向。
  1.  **TODO: `PathTable.java` (对应 `core/include_internal/ten_runtime/path_table/path_table.h` 的 `ten_path_table_t`)**
      - **构建：** 创建 `PathTable` 类。
      - **属性：**
        - `Engine ownerEngine`：**引用**其所属的 `Engine` 实例。
        - `Map<Location, ExtensionInterface> routes`：存储路由规则，Key 为目的地 `Location`，Value 为目标 `Extension` 实例。
      - **组合：** 组合 `Map` 存储路由规则。
      - **依赖：** 依赖 `Engine` (引用), `Message`, `Location`, `ExtensionInterface`。
      - **方法：**
        - `addRoute(Location destination, ExtensionInterface extension)`：添加路由规则。
        - `routeMessage(Message message)`：根据 `Message` 的 `destLoc` 查找并调用对应 `Extension` 的 `onMessage` 方法。处理无法路由的情况。

## 阶段四：消息流转与跨实体通信

### 子阶段 4.1：Engine 消息处理循环

- **目标：** 确保 `Engine` 内部的消息处理循环能够正确地从队列中取出消息，并利用 `PathTable` 进行路由。
- **设计细节：** 遵循 `Engine` 单线程处理的原则，避免阻塞。
  1.  **TODO: 完善 `Engine.java` 中的 `startMessageProcessingLoop()`**
      - **实现：** 确保消息循环在 `engineExecutor` (单线程) 上运行，从 `inMsgsQueue` 阻塞/非阻塞地获取消息。
      - **细节：** 考虑队列为空时的短暂等待策略，避免 CPU 占用过高。

### 子阶段 4.2：Connection 迁移逻辑

- **目标：** 完善 `App` 和 `Engine` 中与 `Connection` 迁移相关的逻辑。
- **设计细节：** 明确 `Connection` 状态机的转换，以及所有权转移的触发点。
  1.  **TODO: 完善 `App.handleIncomingConnectionMessage()`**
      - **实现：** 详细判断 `Connection` 的迁移状态，并调用 `connection.attachToEngine()` 和 `engine.addOrphanConnection()`。
      - **细节：** 确保 `StartGraphCommand` 处理后，后续的同 `Connection` 消息直接进入 `Engine` 队列。

  2.  **TODO: 完善 `Connection.attachToEngine()`**
      - **实现：** 更新 `attachedTarget` 和 `migrationState`，并调用 `protocol.onConnectionMigrated()`。

### 子阶段 4.3：Remote 模型和管理

- **目标：** 实现 `Remote` 模型及其在 `Engine` 中的管理，支撑跨 `Engine`/`App` 通信。
- **设计细节：** `Remote` 是 `Engine` 视角下的远端逻辑实体，通过 `targetAppUri` 和 `targetGraphId` 标识。
  1.  **TODO: `Remote.java` (对应 `core/include_internal/ten_runtime/remote/remote.h` 的 `ten_remote_t`)**
      - **构建：** 创建 `Remote` 类。
      - **属性：**
        - `String remoteId`：`Remote` 实例的唯一 ID。
        - `String targetAppUri`：目标 `App` 的 URI。
        - `String targetGraphId`：目标 `Graph` 的 ID。
        - `Connection associatedConnection`：**引用**关联的底层 `Connection`（简化，实际可能管理多个）。
        - `Engine ownerEngine`：**引用**其所属的 `Engine` 实例。
      - **组合：** 组合 `Connection` (引用)。
      - **依赖：** 依赖 `Engine` (引用), `Connection` (引用), `Message`, `Location`。
      - **方法：**
        - 构造函数：接收 `ownerEngine` 和 `targetAppUri/targetGraphId`，以及可选的 `initialConnection`。
        - `sendMessage(Message message)`：通过关联的 `Connection` 发送消息到远端。
        - `handleIncomingMessage(Message message)`：处理来自其 `Connection` 的入站消息（通常会转发给 `ownerEngine`）。
      - **ID 关联：** `targetAppUri` 和 `targetGraphId` 是 `Remote` 的核心 Key，用于在 `Engine.remotes` 中查找。

  2.  **TODO: 完善 `Engine.java` 中的 `remotes` 管理**
      - **实现：** `Map<String, Remote> remotes` 存储 `Remote` 实例，Key 组合 `targetAppUri` 和 `targetGraphId`。
      - **方法：** `getOrCreateRemote(String targetAppUri, String targetGraphId, Connection initialConnection)` 方法，负责查找或创建 `Remote` 实例。

### 子阶段 4.4：完整的消息发送与回传链路

- **目标：** 确保 `Message` 能够从 `Extension` 经过 `Engine`，并通过 `Remote` 或 `Connection` 正确回传到发起方。
- **设计细节：** 消息回传是出站消息流的重要组成部分，`Remote` 在此扮演关键角色。
  1.  **TODO: 完善 `EngineRuntimeEnv.sendMessageToEngine()`**
      - **实现：** `Extension` 调用此方法时，将消息重新放入 `Engine` 的 `inMsgsQueue`。

  2.  **TODO: 完善 `Engine.startMessageProcessingLoop()` 和 `PathTable.routeMessage()`**
      - **实现：** 在 `Engine` 接收到 `Message` 后，如果它是出站消息（例如 `CommandResult`），并且 `destLoc` 指向外部或特定 `Connection`，则通过 `Remote` 或直接的 `Connection` 发送。
      - **细节：** 判断 `Message` 的 `destLoc`，如果指向外部 `App/Engine`，则通过 `Engine.getOrCreateRemote()` 获取 `Remote` 并调用 `remote.sendMessage()`。如果指向当前 `Engine` 所绑定的某个 `Connection`（例如来自 `orphanConnections`），则直接通过 `Connection` 发送。

## 阶段五：可运行的开发环境骨架

### 子阶段 5.1：基础启动和关闭流程

- **目标：** 实现 `App` 和 `Engine` 的完整生命周期管理，确保它们能够正确启动和关闭。
- **设计细节：** 线程池的启动与关闭、资源清理。
  1.  **TODO: 完善所有 `shutdown()` 方法**
      - **实现：** `App.shutdown()` 负责调用所有 `Engine.shutdown()`，`Engine.shutdown()` 负责关闭自己的 `ExecutorService`，清理 `Extension`、`Remote` 等资源。确保线程安全关闭。

### 子阶段 5.2：简化的示例场景

- **目标：** 创建一个简单的 `main` 方法，演示 `App`、`Connection`、`Engine` 和 `Extension` 之间的基本交互。
- **设计细节：** 模拟前端连接、发送 `start_graph` 命令、数据流转、以及响应回传。
  1.  **TODO: `Main.java` 或 `DemoApp.java`**
      - **实现：**
        - 创建 `App` 实例。
        - 创建 `Protocol` 实例。
        - 模拟一个或多个客户端 `Connection`。
        - 模拟客户端发送 `StartGraphCommand` 和业务数据 `Message`。
        - 观察控制台输出，验证 `Connection` 迁移、`Engine` 创建、`Extension` 处理和消息回传链路。
      - **细节：** 尽可能简化 `Graph JSON` 定义，只包含一两个 `Extension`。

### 子阶段 5.3：测试用例骨架

- **目标：** 为核心组件提供基本的单元测试/集成测试骨架。
- **设计细节：** 确保核心功能按预期工作。
  1.  **TODO: `AppTest.java`, `EngineTest.java`, `ConnectionTest.java` (JUnit)**
      - **实现：** 编写测试用例，验证：
        - `App` 能正确创建和管理 `Engine`。
        - `Connection` 能正确进行迁移。
        - `Engine` 能正确加载 `Extension` 并路由消息。
        - 消息能在 `Engine` 内部正确流转。
        - 简单的消息发送和接收链路。

---

**重要提示：**

- **逐步实现**：这个 TODO 列表是庞大而复杂的。请务必严格按照子阶段和子子阶段的顺序逐步实现。
- **测试驱动**：在每个子阶段完成后，尝试编写测试用例来验证其功能，确保每一步的正确性。
- **日志输出**：在开发过程中，大量使用 `System.out.println()` (在实际项目中应使用成熟的日志框架) 打印关键信息，帮助你追踪消息流、线程切换和状态变化。
- **错误处理**：虽然不是首要关注点，但在实现过程中要考虑潜在的错误情况，并留下错误处理的接口或占位符。

祝你在这次重构任务中一切顺利！请记住，这是一个学习和构建复杂系统的绝佳机会。
