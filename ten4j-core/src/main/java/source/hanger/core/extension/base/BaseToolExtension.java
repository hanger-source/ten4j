package source.hanger.core.extension.base;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import source.hanger.core.extension.base.tool.LLMTool;
import source.hanger.core.extension.component.tool.ExtensionToolDelegate;
import source.hanger.core.message.command.Command;
import source.hanger.core.tenenv.TenEnv;

import static source.hanger.core.common.ExtensionConstants.CMD_TOOL_CALL;

/**
 * @author fuhangbo.hanger.uhfun
 **/
@Slf4j
public abstract class BaseToolExtension extends BaseExtension {

    protected ExtensionToolDelegate extensionToolDelegate;
    @Override
    protected void onExtensionConfigure(TenEnv env, Map<String, Object> properties) {
        extensionToolDelegate = new ExtensionToolDelegate() {
            @Override
            public List<LLMTool> initTools() {
                return BaseToolExtension.this.initTools(env);
            }
        };
    }

    @Override
    public void onStart(TenEnv env) {
        super.onStart(env);
        // 发送注册工具
        extensionToolDelegate.sendRegisterToolCommands(env);
    }

    @Override
    public void onCmd(TenEnv env, Command command) {
        // 处理 CMD_TOOL_CALL 命令
        if (CMD_TOOL_CALL.equals(command.getName())) {
            log.info("[{}] 收到 CMD_TOOL_CALL 命令，处理工具调用。", env.getExtensionName());
            extensionToolDelegate.handleToolCallCommand(env, command);
            return;
        }
    }

    /**
     * 抽象方法：获取此扩展提供的 LLM 工具列表。
     * 子类应实现此方法以返回其支持的 LLMTool 实例列表。
     *
     * @param env 当前的 TenEnv 环境。
     * @return 此扩展提供的 LLMTool 实例列表。
     */
    protected abstract List<LLMTool> initTools(TenEnv env);
}
