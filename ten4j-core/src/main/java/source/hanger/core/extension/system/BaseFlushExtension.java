package source.hanger.core.extension.system;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;
import io.reactivex.schedulers.Schedulers;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.common.ExtensionConstants;
import source.hanger.core.extension.base.BaseExtension;
import source.hanger.core.message.CommandResult;
import source.hanger.core.message.Message;
import source.hanger.core.message.MessageType;
import source.hanger.core.message.command.Command;
import source.hanger.core.message.command.GenericCommand;
import source.hanger.core.tenenv.TenEnv;
import source.hanger.core.util.MessageUtils;

/**
 * 带有输入刷新（中断）功能的抽象基类
 * 提供了流式处理和中断机制的通用实现。
 *
 * @param <T> 流中元素的类型
 */
@Slf4j
public abstract class BaseFlushExtension<T> extends BaseExtension {

    protected final AtomicBoolean interrupted = new AtomicBoolean(false);

    // 统一的流发布器，所有流通过它排队并依次执行，保证顺序，toSerialized保证线程安全
    protected FlowableProcessor<StreamPayload<T>> streamProcessor = PublishProcessor.<StreamPayload<T>>create()
        .toSerialized();
    // 维护所有活跃流的 Disposable，支持取消
    protected Disposable disposable;

    public BaseFlushExtension() {
        super();
    }

    /**
     * 启动时调用
     */
    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        log.info("[{}] 扩展启动", env.getExtensionName());
        // isRunning = true; // Handled by BaseExtension
        interrupted.set(false);
        disposable = generateDisposable(env);
    }

    protected Disposable generateDisposable(TenEnv env) {
        return streamProcessor
            .onBackpressureBuffer()
            .concatMap(payload -> payload.flowable()
                .map(item -> new ItemAndMessage<>(item, payload.originalMessage())) // Map to carry original
                // message
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                // 中断检测，flush后中断当前流处理
                .takeWhile(itemAndMessage -> !interrupted.get()))
            .subscribe(
                itemAndMessage -> handleStreamItem(itemAndMessage.item(),
                    itemAndMessage.originalMessage(), env),
                error -> log.error("{} 流异常", env.getExtensionName(), error),
                () -> log.info("[{}] 流处理完成", env.getExtensionName()));
    }

    /**
     * 停止时调用，取消所有流
     */
    @Override
    public void onStop(TenEnv env) {
        super.onStop(env);
        log.info("[{}] 扩展停止", env.getExtensionName());
        // isRunning = false; // Handled by BaseExtension
        interrupted.set(true);
        disposeCurrent(env);
    }

    /**
     * 清理时调用
     */
    @Override
    public void onDeinit(TenEnv env) {
        super.onDeinit(env);
        log.info("[{}] 扩展清理", env.getExtensionName());
        disposeCurrent(env);
    }

    protected synchronized void disposeCurrent(TenEnv env) {
        if (disposable != null && !disposable.isDisposed()) {
            try {
                disposable.dispose();
                log.debug("[{}] 已取消当前流订阅", env.getExtensionName());
            } catch (Exception e) {
                log.warn("[{}] 取消流订阅异常", env.getExtensionName(), e);
            }
        }
    }

    /**
     * 刷新输入，取消所有待处理和活跃流
     * 重新创建流发布器和订阅，保证新流可以正常消费
     */
    public synchronized void flushInputItems(TenEnv env, Command command) {
        log.info("[{}] 刷新输入并取消活跃流: extensionName={}", env.getExtensionName(), env.getExtensionName());
        onCancelFlush(env); // 调用子类特定的取消逻辑
        interrupted.set(true); // 先设置中断，切断当前流的处理
        disposeCurrent(env); // 取消订阅，释放资源
        // 重新创建流发布器（线程安全的）
        streamProcessor = PublishProcessor.<StreamPayload<T>>create().toSerialized();
        interrupted.set(false); // 解除中断，准备处理新流
        disposable = generateDisposable(env); // 重新订阅新流
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        if (!isRunning()) { // Use isRunning() from BaseExtension
            log.warn("[{}] 扩展未运行，忽略命令: extensionName={}, commandName={}", env.getExtensionName(),
                env.getExtensionName(),
                command.getName());
            return;
        }
        log.info("[{}] Received command: {}", env.getExtensionName(), command.getName());
        try {
            String commandName = command.getName();
            if (ExtensionConstants.CMD_IN_FLUSH.equals(commandName)) {
                flushInputItems(env, command);
                Command outFlushCmd = GenericCommand.create(ExtensionConstants.CMD_OUT_FLUSH, command.getId(),
                    command.getType());
                log.info("[{}] 传递 flush: {}", env.getExtensionName(), commandName);
                env.sendCmd(outFlushCmd);
                log.info("[{}] 命令处理完成: {}", env.getExtensionName(), commandName);
            } else {
                CommandResult result = CommandResult.success(command,
                    "未知%s命令，忽略".formatted(env.getExtensionName()));
                env.sendResult(result);
            }
        } catch (Exception e) {
            log.error("[{}] 扩展命令处理异常: extensionName={}, commandName={}", env.getExtensionName(),
                env.getExtensionName(),
                command.getName(), e);
            sendErrorResult(env, command.getId(), command.getType(), "",
                "%s命令处理异常: %s".formatted(env.getExtensionName(), e.getMessage()));
        }
    }

    // 发送错误结果
    protected void sendErrorResult(TenEnv env, String messageId, MessageType messageType, String messageName,
        String errorMessage) {
        String finalMessageId = (messageId != null && !messageId.isEmpty()) ? messageId
            : MessageUtils.generateUniqueId();
        CommandResult errorResult = CommandResult.fail(finalMessageId, messageType, messageName, errorMessage);
        env.sendResult(errorResult);
    }

    /**
     * 子类必须实现此方法来处理从流中接收到的每个数据项。
     *
     * @param item            从流中接收到的数据项
     * @param originalMessage 触发此流的原始消息
     * @param env             当前的TenEnv环境
     */
    protected abstract void handleStreamItem(T item, Message originalMessage, TenEnv env);

    /**
     * 子类必须实现此方法以执行特定的取消逻辑，例如停止底层服务。
     * 在发生输入刷新（中断）时调用。
     *
     * @param env 当前的TenEnv环境
     */
    protected abstract void onCancelFlush(TenEnv env);

    /**
     * 内部类，用于封装流和原始消息
     */
    public record StreamPayload<T>(Flowable<T> flowable, Message originalMessage) {
    }

    /**
     * 内部类，用于封装流中的数据项和原始消息
     */
    public record ItemAndMessage<T>(T item, Message originalMessage) {
    }
}