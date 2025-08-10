# `ten-framework-bridge` 消息对齐探查索引

本文档旨在为 `ten-framework-bridge` 项目中 Java 消息类与 C/Python `ten-framework` 核心消息协议的对齐提供一个详细的探查索引。它记录了每个 Java 消息类对应的 C 语言结构体定义、关键字段的源码位置、类型以及在 Java 端的映射和特殊处理（特别是 `properties.ten` 的序列化）。

**目标:** 确保 Java 端消息协议的实现与 C/Python 端底层设计保持**事无巨细的精确对齐**，并为未来的源码追溯和问题排查提供便利。

---

## 1. 核心消息模型

### 1.1 `Message.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/Message.java`
- **对应 C 结构体:** `ten_msg_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/msg.h` (L42-62)

- **核心字段对齐:**
  - `id` (Java `String`)
    - **C 对应:** `ten_msg_t.name`
    - **C 类型:** `ten_value_t` (内部为 `string`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/msg.h` (L52)
    - **说明:** 用于消息路由和识别消息的名称。对于命令消息，它通常代表命令的类型（如 "start_graph"）。
  - `type` (Java `MessageType` 枚举)
    - **C 对应:** `ten_msg_t.type`
    - **C 类型:** `TEN_MSG_TYPE` (内部为 `uint32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/msg.h` (L46)
    - **说明:** 标识消息的具体类型（如 `CMD`, `DATA`, `VIDEO_FRAME` 等）。
  - `srcLoc` (Java `Location` 对象)
    - **C 对应:** `ten_msg_t.src_loc`
    - **C 类型:** `ten_loc_t`
    - **C 源码:** `core/include_internal/ten_runtime/msg/msg.h` (L54)
    - **说明:** 消息的源位置信息。
  - `destLocs` (Java `List<Location>` 对象)
    - **C 对应:** `ten_msg_t.dest_loc`
    - **C 类型:** `ten_list_t` (内部存储 `ten_loc_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/msg.h` (L55)
    - **说明:** 消息的目的位置列表。
  - `properties` (Java `Map<String, Object>`)
    - **C 对应:** `ten_msg_t.properties`
    - **C 类型:** `ten_value_t` (内部为 `object value`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/msg.h` (L57)
    - **说明:** 用于承载动态的、非硬编码的属性。**特别注意：** C 端一些**硬编码的子结构体字段**（如 `ten_video_frame_t` 中的 `width`、`height`）在序列化时也会被放入这个 `properties` 字段下的 `"ten"` 子对象中（例如 `properties.ten.width`）。这导致 Java 端需要自定义 Jackson 序列化器和反序列化器来正确处理这种映射。
  - `timestamp` (Java `long`)
    - **C 对应:** `ten_msg_t.timestamp`
    - **C 类型:** `int64_t`
    - **C 源码:** `core/include_internal/ten_runtime/msg/msg.h` (L61)
    - **说明:** 消息的创建时间戳。

### 1.2 `MessageType.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/MessageType.java`
- **对应 C 枚举:** `TEN_MSG_TYPE`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/msg.h` (L37-47)

- **对齐说明:** Java 枚举 `MessageType` 与 C 语言 `TEN_MSG_TYPE` 枚举保持完全一致，包括类型、顺序和值。`TEN_MSG_TYPE_LAST` 是 C 端内部占位符，无需在 Java 枚举中体现。

### 1.3 `Location.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/Location.java`
- **对应 C 结构体:** `ten_loc_t`
- **C 源码定义:** `core/include_internal/ten_runtime/common/loc.h` (L12-19)

- **核心字段对齐:**
  - `appUri` (Java `String`)
    - **C 对应:** `ten_loc_t.app_uri`
    - **C 类型:** `ten_string_t`
    - **C 源码:** `core/include_internal/ten_runtime/common/loc.h` (L14)
    - **说明:** 应用的URI。
  - `graphId` (Java `String`)
    - **C 对应:** `ten_loc_t.graph_id`
    - **C 类型:** `ten_string_t`
    - **C 源码:** `core/include_internal/ten_runtime/common/loc.h` (L15)
    - **说明:** 图的ID。
  - `extensionName` (Java `String`)
    - **C 对应:** `ten_loc_t.extension_name`
    - **C 类型:** `ten_string_t`
    - **C 源码:** `core/include_internal/ten_runtime/common/loc.h` (L16)
    - **说明:** 扩展的名称。

## 2. 具体消息类型

### 2.1 `CommandMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/CommandMessage.java`
- **对应 C 结构体:** `ten_cmd_t` (通用命令头)
- **C 源码定义:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/cmd.h` (L16-20)
- **基命令结构体:** `ten_cmd_base_t`
- **基命令源码定义:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_base.h` (L20-34)

- **核心字段对齐:** (除了继承自 `Message` 的字段外)
  - `cmdId` (Java `String`)
    - **C 对应:** `ten_cmd_base_t.cmd_id`
    - **C 类型:** `ten_value_t` (内部为 `string`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_base.h` (L29)
    - **序列化路径:** `properties.ten.cmd_id` (C 源码处理: `core/src/ten_runtime/msg/cmd_base/cmd_base.c` 约L152, `ten_raw_cmd_base_process_field`)
    - **说明:** 命令的唯一标识符，TEN 运行时内部使用。
  - `seqId` (Java `String`)
    - **C 对应:** `ten_cmd_base_t.seq_id`
    - **C 类型:** `ten_value_t` (内部为 `string`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_base.h` (L30)
    - **序列化路径:** `properties.ten.seq_id` (C 源码处理: `core/src/ten_runtime/msg/cmd_base/cmd_base.c` 约L152, `ten_raw_cmd_base_process_field`)
    - **说明:** 序列号标识符，主要用于 TEN 客户端进行命令跟踪。
  - `parentCmdId` (Java `String`)
    - **C 对应:** `ten_cmd_base_t.parent_cmd_id`
    - **C 类型:** `ten_string_t`
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_base.h` (L27)
    - **序列化路径:** `properties.ten.parent_cmd_id` (C 源码处理: `core/src/ten_runtime/msg/cmd_base/cmd_base.c` 约L152, `ten_raw_cmd_base_process_field`)
    - **说明:** 父命令 ID，用于在命令被克隆时建立其与源命令的关系。
- **特殊说明:** `CommandMessage` 的 `id` 字段（继承自 `Message`）在此处作为命令的**名称/类型标识**。命令的实际参数则通过继承自 `Message` 的 `properties` `Map` 承载。**需要为 `CommandMessage` 实现自定义的 Jackson `JsonSerializer` (`CommandMessageSerializer.java`) 和 `JsonDeserializer` (`CommandMessageDeserializer.java`) 来正确处理其硬编码字段在 `properties.ten` 下的序列化和反序列化。**

### 2.2 `DataMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/DataMessage.java`
- **对应 C 结构体:** `ten_data_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/data/data.h` (L18-23)

- **核心字段对齐:** (除了继承自 `Message` 的字段外)
  - `data` (Java `byte[]`)
    - **C 对应:** `ten_data_t.data`
    - **C 类型:** `ten_value_t` (内部为 `buf`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/data/data.h` (L21)
    - **序列化路径:** `properties.ten.data` (C 源码处理: `core/src/ten_runtime/msg/data/data.c` 约L100, `ten_raw_data_loop_all_fields`)
    - **说明:** 实际的二进制数据内容。
- **特殊说明:** **需要为 `DataMessage` 实现自定义的 Jackson `JsonSerializer` (`DataMessageSerializer.java`) 和 `JsonDeserializer` (`DataMessageDeserializer.java`) 来正确处理其硬编码字段在 `properties.ten` 下的序列化和反序列化。**

### 2.3 `VideoFrameMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/VideoFrameMessage.java`
- **对应 C 结构体:** `ten_video_frame_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/video_frame/video_frame.h` (L21-31)

- **核心字段对齐:** (除了继承自 `Message` 的字段外)
  - `pixelFormat` (Java `int`)
    - **C 对应:** `ten_video_frame_t.pixel_fmt`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`, `TEN_PIXEL_FMT`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/video_frame/video_frame.h` (L25)
    - **序列化路径:** `properties.ten.pixel_fmt` (C 源码处理: `core/src/ten_runtime/msg/video_frame/video_frame.c` 约L85, `ten_raw_video_frame_set_ten_property`)
    - **说明:** 视频帧的像素格式。
  - `frameTimestamp` (Java `long`)
    - **C 对应:** `ten_video_frame_t.timestamp`
    - **C 类型:** `ten_value_t` (内部为 `int64_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/video_frame/video_frame.h` (L26)
    - **序列化路径:** `properties.ten.timestamp`
    - **说明:** 视频帧的时间戳。
  - `width` (Java `int`)
    - **C 对应:** `ten_video_frame_t.width`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/video_frame/video_frame.h` (L27)
    - **序列化路径:** `properties.ten.width`
    - **说明:** 视频帧的宽度。
  - `height` (Java `int`)
    - **C 对应:** `ten_video_frame_t.height`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/video_frame/video_frame.h` (L28)
    - **序列化路径:** `properties.ten.height`
    - **说明:** 视频帧的高度。
  - `isEof` (Java `boolean`)
    - **C 对应:** `ten_video_frame_t.is_eof`
    - **C 类型:** `ten_value_t` (内部为 `bool`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/video_frame/video_frame.h` (L29)
    - **序列化路径:** `properties.ten.is_eof`
    - **说明:** 是否为文件结束帧。
  - `data` (Java `byte[]`)
    - **C 对应:** `ten_video_frame_t.data`
    - **C 类型:** `ten_value_t` (内部为 `buf`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/video_frame/video_frame.h` (L30)
    - **序列化路径:** `properties.ten.data`
    - **说明:** 实际的视频帧数据。
- **特殊说明:** C 端其他非硬编码字段（如 `fps`, `pts`, `dts`）不再作为 Java 类中的直接字段，而是通过 `Message.properties` 访问。**需要为 `VideoFrameMessage` 实现自定义的 Jackson `JsonSerializer` (`VideoFrameMessageSerializer.java`) 和 `JsonDeserializer` (`VideoFrameMessageDeserializer.java`) 来正确处理其硬编码字段在 `properties.ten` 下的序列化和反序列化。**

### 2.4 `AudioFrameMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/AudioFrameMessage.java`
- **对应 C 结构体:** `ten_audio_frame_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L19-58)

- **核心字段对齐:** (除了继承自 `Message` 的字段外)
  - `frameTimestamp` (Java `long`)
    - **C 对应:** `ten_audio_frame_t.timestamp`
    - **C 类型:** `ten_value_t` (内部为 `int64_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L23)
    - **序列化路径:** `properties.ten.timestamp` (C 源码处理类似视频帧)
    - **说明:** 音频帧的时间戳。
  - `sampleRate` (Java `int`)
    - **C 对应:** `ten_audio_frame_t.sample_rate`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L24)
    - **序列化路径:** `properties.ten.sample_rate`
    - **说明:** 音频帧的采样率。
  - `bytesPerSample` (Java `int`)
    - **C 对应:** `ten_audio_frame_t.bytes_per_sample`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L25)
    - **序列化路径:** `properties.ten.bytes_per_sample`
    - **说明:** 每样本的字节数。
  - `samplesPerChannel` (Java `int`)
    - **C 对应:** `ten_audio_frame_t.samples_per_channel`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L26)
    - **序列化路径:** `properties.ten.samples_per_channel`
    - **说明:** 每通道的样本数。
  - `numberOfChannel` (Java `int`)
    - **C 对应:** `ten_audio_frame_t.number_of_channel`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L27)
    - **序列化路径:** `properties.ten.number_of_channel`
    - **说明:** 通道数。
  - `channelLayout` (Java `long`)
    - **C 对应:** `ten_audio_frame_t.channel_layout`
    - **C 类型:** `ten_value_t` (内部为 `uint64_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L37)
    - **序列化路径:** `properties.ten.channel_layout`
    - **说明:** FFmpeg 的通道布局 ID。
  - `dataFormat` (Java `int`)
    - **C 对应:** `ten_audio_frame_t.data_fmt`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`, `TEN_AUDIO_FRAME_DATA_FMT`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L39)
    - **序列化路径:** `properties.ten.data_fmt`
    - **说明:** 数据格式。
  - `buf` (Java `byte[]`)
    - **C 对应:** `ten_audio_frame_t.buf`
    - **C 类型:** `ten_value_t` (内部为 `buf`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L41)
    - **序列化路径:** `properties.ten.buf`
    - **说明:** 实际的音频帧数据。
  - `lineSize` (Java `int`)
    - **C 对应:** `ten_audio_frame_t.line_size`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L55)
    - **序列化路径:** `properties.ten.line_size`
    - **说明:** 每通道的数据大小。
  - `isEof` (Java `boolean`)
    - **C 对应:** `ten_audio_frame_t.is_eof`
    - **C 类型:** `ten_value_t` (内部为 `bool`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/audio_frame/audio_frame.h` (L57)
    - **序列化路径:** `properties.ten.is_eof`
    - **说明:** 是否为文件结束帧。
- **特殊说明:** C 端其他非硬编码字段（如 `bits_per_sample`, `format`）不再作为 Java 类中的直接字段，而是通过 `Message.properties` 访问。**需要为 `AudioFrameMessage` 实现自定义的 Jackson `JsonSerializer` (`AudioFrameMessageSerializer.java`) 和 `JsonDeserializer` (`AudioFrameMessageDeserializer.java`) 来正确处理其硬编码字段在 `properties.ten` 下的序列化和反序列化。**

### 2.5 `CommandResultMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/CommandResultMessage.java`
- **对应 C 结构体:** `ten_cmd_result_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_result/cmd.h` (L18-25)

- **核心字段对齐:** (除了继承自 `Message` 和 `CommandMessage` 的字段外)
  - `originalCmdType` (Java `int`)
    - **C 对应:** `ten_cmd_result_t.original_cmd_type`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`, `TEN_MSG_TYPE`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_result/cmd.h` (L21)
    - **序列化路径:** `properties.ten.original_cmd_type` (C 源码处理类似其他硬编码字段)
    - **说明:** 原始命令的消息类型。
  - `originalCmdName` (Java `String`)
    - **C 对应:** `ten_cmd_result_t.original_cmd_name`
    - **C 类型:** `ten_value_t` (内部为 `string`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_result/cmd.h` (L22)
    - **序列化路径:** `properties.ten.original_cmd_name`
    - **说明:** 原始命令的名称。
  - `statusCode` (Java `int`)
    - **C 对应:** `ten_cmd_result_t.status_code`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`, `TEN_STATUS_CODE`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_result/cmd.h` (L23)
    - **序列化路径:** `properties.ten.status_code`
    - **说明:** 命令执行结果的状态码。
  - `isFinal` (Java `boolean`)
    - **C 对应:** `ten_cmd_result_t.is_final`
    - **C 类型:** `ten_value_t` (内部为 `bool`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_result/cmd.h` (L24)
    - **序列化路径:** `properties.ten.is_final`
    - **说明:** 结果是否为最终结果。
  - `isCompleted` (Java `boolean`)
    - **C 对应:** `ten_cmd_result_t.is_completed`
    - **C 类型:** `ten_value_t` (内部为 `bool`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd_result/cmd.h` (L25)
    - **序列化路径:** `properties.ten.is_completed`
    - **说明:** 命令是否已完成。
- **特殊说明:** 结果的实际 `payload` (负载) 或 `result_message` 等非硬编码信息将通过继承自 `Message` 的 `properties` `Map` 访问。**需要为 `CommandResultMessage` 实现自定义的 Jackson `JsonSerializer` (`CommandResultMessageSerializer.java`) 和 `JsonDeserializer` (`CommandResultMessageDeserializer.java`) 来正确处理其硬编码字段在 `properties.ten` 下的序列化和反序列化。**

### 2.6 `StartGraphCommandMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/command/StartGraphCommandMessage.java`
- **对应 C 结构体:** `ten_cmd_start_graph_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/start_graph/cmd.h` (L18-24)

- **核心字段对齐:** (除了继承自 `CommandMessage` 的字段外)
  - `longRunningMode` (Java `Boolean`)
    - **C 对应:** `ten_cmd_start_graph_t.long_running_mode`
    - **C 类型:** `ten_value_t` (内部为 `bool`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/start_graph/cmd.h` (L20)
    - **序列化路径:** `properties.ten.long_running_mode`
    - **说明:** 是否为长时运行模式。
  - `predefinedGraphName` (Java `String`)
    - **C 对应:** `ten_cmd_start_graph_t.predefined_graph_name`
    - **C 类型:** `ten_value_t` (内部为 `string`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/start_graph/cmd.h` (L21)
    - **序列化路径:** `properties.ten.predefined_graph_name`
    - **说明:** 预定义的图名称。
  - `extensionGroupsInfo` (Java `List<ExtensionGroupInfo>`)
    - **C 对应:** `ten_cmd_start_graph_t.extension_groups_info`
    - **C 类型:** `ten_value_t` (内部为 `list` of `object value`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/start_graph/cmd.h` (L22)
    - **序列化路径:** `properties.ten.extension_groups_info`
    - **说明:** 扩展组信息列表。
  - `extensionsInfo` (Java `List<ExtensionInfo>`)
    - **C 对应:** `ten_cmd_start_graph_t.extensions_info`
    - **C 类型:** `ten_value_t` (内部为 `list` of `object value`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/start_graph/cmd.h` (L23)
    - **序列化路径:** `properties.ten.extensions_info`
    - **说明:** 扩展信息列表。
  - `graphJson` (Java `String`)
    - **C 对应:** `ten_cmd_start_graph_t.graph_json`
    - **C 类型:** `ten_value_t` (内部为 `string`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/start_graph/cmd.h` (L24)
    - **序列化路径:** `properties.ten.graph_json`
    - **说明:** 图的 JSON 定义。
- **特殊说明:** **需要为 `StartGraphCommandMessage` 实现自定义的 Jackson `JsonSerializer` (`StartGraphCommandMessageSerializer.java`) 和 `JsonDeserializer` (`StartGraphCommandMessageDeserializer.java`) 来正确处理其硬编码字段在 `properties.ten` 下的序列化和反序列化。**

### 2.7 `StopGraphCommandMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/command/StopGraphCommandMessage.java`
- **对应 C 结构体:** `ten_cmd_stop_graph_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/stop_graph/cmd.h` (L18-20)

- **核心字段对齐:** (除了继承自 `CommandMessage` 的字段外)
  - `graphId` (Java `String`)
    - **C 对应:** `ten_cmd_stop_graph_t.graph_id`
    - **C 类型:** `ten_value_t` (内部为 `string`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/stop_graph/cmd.h` (L20)
    - **序列化路径:** `properties.ten.graph_id`
    - **说明:** 要停止的目标 Engine ID (即 `graph_id`)。
- **特殊说明:** **需要为 `StopGraphCommandMessage` 实现自定义的 Jackson `JsonSerializer` (`StopGraphCommandMessageSerializer.java`) 和 `JsonDeserializer` (`StopGraphCommandMessageDeserializer.java`) 来正确处理其硬编码字段在 `properties.ten` 下的序列化和反序列化。**

### 2.8 `TimerCommandMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/command/TimerCommandMessage.java`
- **对应 C 结构体:** `ten_cmd_timer_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/timer/cmd.h` (L18-22)

- **核心字段对齐:** (除了继承自 `CommandMessage` 的字段外)
  - `timerId` (Java `Long`)
    - **C 对应:** `ten_cmd_timer_t.timer_id`
    - **C 类型:** `ten_value_t` (内部为 `uint32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/timer/cmd.h` (L20)
    - **序列化路径:** `properties.ten.timer_id`
    - **说明:** 定时器的唯一 ID。
  - `timeoutUs` (Java `Long`)
    - **C 对应:** `ten_cmd_timer_t.timeout_us`
    - **C 类型:** `ten_value_t` (内部为 `uint64_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/timer/cmd.h` (L21)
    - **序列化路径:** `properties.ten.timeout_us`
    - **说明:** 超时时间（微秒）。
  - `times` (Java `Integer`)
    - **C 对应:** `ten_cmd_timer_t.times`
    - **C 类型:** `ten_value_t` (内部为 `int32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/timer/cmd.h` (L22)
    - **序列化路径:** `properties.ten.times`
    - **说明:** 定时器触发次数。
- **特殊说明:** **需要为 `TimerCommandMessage` 实现自定义的 Jackson `JsonSerializer` (`TimerCommandMessageSerializer.java`) 和 `JsonDeserializer` (`TimerCommandMessageDeserializer.java`) 来正确处理其硬编码字段在 `properties.ten` 下的序列化和反序列化。**

### 2.9 `TimeoutCommandMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/command/TimeoutCommandMessage.java`
- **对应 C 结构体:** `ten_cmd_timeout_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/timeout/cmd.h` (L18-20)

- **核心字段对齐:** (除了继承自 `CommandMessage` 的字段外)
  - `timerId` (Java `Long`)
    - **C 对应:** `ten_cmd_timeout_t.timer_id`
    - **C 类型:** `ten_value_t` (内部为 `uint32_t`)
    - **C 源码:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/timeout/cmd.h` (L20)
    - **序列化路径:** `properties.ten.timer_id`
    - **说明:** 触发超时的定时器 ID。
- **特殊说明:** **需要为 `TimeoutCommandMessage` 实现自定义的 Jackson `JsonSerializer` (`TimeoutCommandMessageSerializer.java`) 和 `JsonDeserializer` (`TimeoutCommandMessageDeserializer.java`) 来正确处理其硬编码字段在 `properties.ten` 下的序列化和反序列化。**

### 2.10 `CloseAppCommandMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/command/CloseAppCommandMessage.java`
- **对应 C 结构体:** `ten_cmd_close_app_t`
- **C 源码定义:** `core/include_internal/ten_runtime/msg/cmd_base/cmd/close_app/cmd.h` (L18-19)

- **核心字段对齐:** (仅继承自 `CommandMessage`，无额外硬编码字段)
  - **说明:** C 端 `ten_cmd_close_app_t` 只包含 `ten_cmd_t cmd_hdr`，没有额外的硬编码字段。其命令名称由 `Message.id` 承载，无特定参数。**此消息类型不需要自定义序列化器。**

### 2.11 `AddExtensionToGraphCommandMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/command/AddExtensionToGraphCommandMessage.java`
- **对应 C 结构体:** (无直接对应，视为通用 `CMD` 消息，参数通过 `properties` 传递)
- **C 源码位置推断:** 字段根据 `AddExtensionToGraphCommandHandler.java` 反推，可能没有专门的 C 结构体。

- **核心字段对齐:** (通过 `Message.properties` 传递)
  - `graphId` (Java `String`)
    - **对应 `properties` Key:** `graphId`
    - **说明:** 目标图的 ID。
  - `extensionType` (Java `String`)
    - **对应 `properties` Key:** `extensionType`
    - **说明:** 扩展的类型。
  - `extensionName` (Java `String`)
    - **对应 `properties` Key:** `extensionName`
    - **说明:** 扩展的名称。
  - `appUri` (Java `String`)
    - **对应 `properties` Key:** `appUri`
    - **说明:** 扩展所属应用的 URI。
  - `graphJson` (Java `String`)
    - **对应 `properties` Key:** `graphJson`
    - **说明:** 扩展的图 JSON 配置。
- **特殊说明:** 这是一个通用命令，其所有参数都作为 `Message.properties` 的键值对传递，C 端很可能没有与其完全对应的硬编码结构体。**此消息类型不需要自定义序列化器。**

### 2.12 `RemoveExtensionFromGraphCommandMessage.java`

- **Java 文件路径:** `ai/output/ten-core-api/src/main/java/com/tenframework/core/message/command/RemoveExtensionFromGraphCommandMessage.java`
- **对应 C 结构体:** (无直接对应，视为通用 `CMD` 消息，参数通过 `properties` 传递)
- **C 源码位置推断:** 字段根据 `RemoveExtensionFromGraphCommandHandler.java` 反推，可能没有专门的 C 结构体。

- **核心字段对齐:** (通过 `Message.properties` 传递)
  - `graphId` (Java `String`)
    - **对应 `properties` Key:** `graphId`
    - **说明:** 目标图的 ID。

  - `extensionName` (Java `String`)
    - **对应 `properties` Key:** `extensionName`

    - **说明:** 要移除的扩展名称。

  - `appUri` (Java `String`)
    - **对应 `properties` Key:** `appUri`

    - **说明:** 扩展所属应用的 URI。

- **特殊说明:** 这是一个通用命令，其所有参数都作为 `Message.properties` 的键值对传递，C 端很可能没有与其完全对应的硬编码结构体。**此消息类型不需要自定义序列化器。**
