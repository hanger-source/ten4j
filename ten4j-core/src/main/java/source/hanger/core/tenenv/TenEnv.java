package source.hanger.core.tenenv;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import source.hanger.core.extension.Extension;
import source.hanger.core.message.AudioFrameMessage;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.DataMessage;
import source.hanger.core.message.VideoFrameMessage;
import source.hanger.core.message.command.Command;

/**
 * `TenEnv` 接口代表一个组件（App, Engine, Extension）的运行时环境句柄。
 * 它提供了在组件自身的 Runloop 线程上执行操作的方法，以及与框架核心交互的接口。
 * 这是 `ten_env_t` 和 Python `TenEnv` 的 Java 对应。
 */
public interface TenEnv {

    /**
     * 提交一个任务到当前 TenEnv 关联的 Runloop。
     * 这是确保所有操作都在正确的线程上下文中执行的关键。
     *
     * @param task 要执行的任务。
     */
    void postTask(Runnable task);

    // region 消息发送方法（从当前 TenEnv 向外发送）
    CompletableFuture<CommandResult> sendAsyncCmd(Command command);

    default void sendCmd(Command command) {
        sendMessage(command);
    }

    void sendResult(CommandResult result);

    void sendData(DataMessage data);

    void sendVideoFrame(VideoFrameMessage videoFrame);

    void sendAudioFrame(AudioFrameMessage audioFrame);

    void sendMessage(source.hanger.core.message.Message message); // 通用的消息发送方法

    // endregion

    // region 属性访问方法

    Optional<Object> getProperty(String path);

    void setProperty(String path, Object value);

    boolean hasProperty(String path);

    void deleteProperty(String path);

    Optional<Integer> getPropertyInt(String path);

    void setPropertyInt(String path, int value);

    Optional<Long> getPropertyLong(String path);

    void setPropertyLong(String path, long value);

    Optional<String> getPropertyString(String path);

    void setPropertyString(String path, String value);

    Optional<Boolean> getPropertyBool(String path);

    void setPropertyBool(String path, boolean value);

    Optional<Double> getPropertyDouble(String path);

    void setPropertyDouble(String path, double value);

    Optional<Float> getPropertyFloat(String path);

    void setPropertyFloat(String path, float value);

    void initPropertyFromJson(String jsonStr);

    // endregion

    // region 上下文信息方法

    String getAppUri();

    String getGraphId();

    String getExtensionName();

    Extension getAttachedExtension(); // 获取此 TenEnv 所依附的 Extension 实例

    // endregion

    // region 生命周期方法

    void close(); // 关闭 TenEnv

    // endregion
}