package source.hanger.core.extension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import source.hanger.core.tenenv.TenEnv;

/**
 * `ExtensionGroup` 模拟 C 端 `ten_extension_group_t`，
 * 代表一组 Extension 的逻辑集合，并拥有自己的生命周期回调。
 */
@Getter
@Slf4j
public class ExtensionGroup {

    private final String name;
    private final ExtensionGroupInfo extensionGroupInfo;
    private final Map<String, ExtensionEnvImpl> extensions = new HashMap<>();
    private final Map<String, ExtensionInfo> extensionInfos = new HashMap<>();
    @Setter
    private TenEnv tenEnv;

    public ExtensionGroup(String name, ExtensionGroupInfo extensionGroupInfo) {
        this.name = name;
        this.extensionGroupInfo = extensionGroupInfo;
    }

    // C 端 on_configure
    public void onConfigure(TenEnv env) {
        log.info("ExtensionGroup {}: onConfigure called.", name);
        // 实现 ExtensionGroup 自身的配置逻辑
        // 例如，根据 extensionGroupInfo.getProperty() 进行配置
        // 【修正】使用 this.tenEnv 而不是传入的 env
        if (this.tenEnv != null) {
            // 示例：可以从 this.tenEnv 获取配置或执行其他操作
            this.tenEnv.postTask(() -> log.info("ExtensionGroup {}: onConfigure task posted to its own TenEnv.", name));
        }
    }

    // C 端 on_init
    public void onInit(TenEnv env) {
        log.info("ExtensionGroup {}: onInit called.", name);
        // 实现 ExtensionGroup 自身的初始化逻辑
        // 【修正】使用 this.tenEnv 而不是传入的 env
        if (this.tenEnv != null) {
            this.tenEnv.postTask(() -> log.info("ExtensionGroup {}: onInit task posted to its own TenEnv.", name));
        }
    }

    // C 端 on_deinit
    public void onDeinit(TenEnv env) {
        log.info("ExtensionGroup {}: onDeinit called.", name);
        // 实现 ExtensionGroup 自身的去初始化逻辑
        // 【修正】使用 this.tenEnv 而不是传入的 env
        if (this.tenEnv != null) {
            this.tenEnv.postTask(() -> log.info("ExtensionGroup {}: onDeinit task posted to its own TenEnv.", name));
        }
    }

    // C 端 on_create_extensions
    public void onCreateExtensions(TenEnv env) {
        log.info("ExtensionGroup {}: onCreateExtensions called. Extensions are about to be created.", name);
        // 在这里，ExtensionGroup 可以执行与创建 Extension 相关的逻辑
        // 例如，验证配置，准备资源等
        // 【修正】使用 this.tenEnv 而不是传入的 env
        if (this.tenEnv != null) {
            this.tenEnv.postTask(
                    () -> log.info("ExtensionGroup {}: onCreateExtensions task posted to its own TenEnv.", name));
        }

        // 【新增】遍历并触发所有托管 Extension 的生命周期方法
        extensions.forEach((extensionName, extEnv) -> {
            try {
                log.info("ExtensionGroup {}: Triggering lifecycle for Extension {}.", name, extensionName);
                // 从 extEnv 获取 Extension 实例，并调用其生命周期方法
                Extension extension = extEnv.getExtension();
                if (extension != null) {
                    extension.onConfigure(extEnv, extEnv.getRuntimeInfo().getProperty()); // 从 getRuntimeInfo 获取属性
                    extension.onInit(extEnv);
                    extension.onStart(extEnv);
                }
            } catch (Exception e) {
                log.error("ExtensionGroup {}: Error triggering Extension {} lifecycle: {}",
                        name, extensionName, e.getMessage(), e);
            }
        });
    }

    // C 端 on_destroy_extensions
    public void onDestroyExtensions(TenEnv env, List<Extension> extensionsToDestroy) { // 【修正】添加 TenEnv env 参数
        log.info("ExtensionGroup {}: onDestroyExtensions called. Extensions are about to be destroyed.", name);
        // 在这里，ExtensionGroup 可以执行与销毁 Extension 相关的逻辑
        // 例如，清理共享资源，通知其他组件等
        // 【修正】使用 this.tenEnv 而不是传入的 env
        if (this.tenEnv != null) {
            this.tenEnv.postTask(
                    () -> log.info("ExtensionGroup {}: onDestroyExtensions task posted to its own TenEnv.", name));
        }

        // 【新增】遍历并触发所有待销毁 Extension 的生命周期方法
        for (Extension extension : extensionsToDestroy) {
            String extensionName = extension.getExtensionName();
            ExtensionEnvImpl extensionEnv = extensions.remove(extensionName);
            if (extensionEnv != null) {
                try {
                    log.info("ExtensionGroup {}: Triggering de-lifecycle for Extension {}. 环境: {}", name, extensionName,
                            extensionEnv.toString());
                    extension.onStop(extensionEnv);
                    extension.onDeinit(extensionEnv);
                    extension.onDestroy(extensionEnv);
                } catch (Exception e) {
                    log.error("ExtensionGroup {}: Error triggering Extension {} de-lifecycle: {}",
                            name, extensionName, e.getMessage(), e);
                }
            } else {
                log.warn("ExtensionGroup {}: Extension {} not found in managed extensions for destruction. 已经通过其他方式移除。",
                        name,
                        extensionName);
            }
        }
    }

    public void addManagedExtension(String extensionName, Extension extension, ExtensionEnvImpl extensionEnv,
            ExtensionInfo extInfo) {
        if (extensions.containsKey(extensionName)) {
            log.warn("ExtensionGroup {}: Extension {} already managed.", name, extensionName);
            return;
        }
        extensions.put(extensionName, extensionEnv);
        extensionInfos.put(extensionName, extInfo);
        log.info("ExtensionGroup {}: Managing Extension {}. 实例环境: {}", name, extensionName, extensionEnv.toString());
    }

    /**
     * 获取 ExtensionGroup 中管理的指定 Extension 的 ExtensionEnvImpl 实例。
     *
     * @param extensionId Extension 的唯一 ID。
     * @return 对应的 ExtensionEnvImpl 实例，如果不存在则返回 null。
     */
    public ExtensionEnvImpl getManagedExtensionEnv(String extensionId) {
        return extensions.get(extensionId);
    }

    /**
     * 获取 ExtensionGroup 中管理的指定 Extension 实例。
     *
     * @param extensionId Extension 的唯一 ID。
     * @return 对应的 Extension 实例，如果不存在则返回 null。
     */
    public Extension getExtension(String extensionId) {
        ExtensionEnvImpl env = getManagedExtensionEnv(extensionId);
        return (env != null) ? env.getExtension() : null;
    }

    /**
     * 获取 ExtensionGroup 中管理的指定 Extension 的 ExtensionInfo 实例。
     *
     * @param extensionId Extension 的唯一 ID。
     * @return 对应的 ExtensionInfo 实例，如果不存在则返回 null。
     */
    public ExtensionInfo getExtensionInfo(String extensionId) {
        ExtensionEnvImpl env = getManagedExtensionEnv(extensionId);
        if (env != null && env.getRuntimeInfo() instanceof ExtensionInfo) {
            return (ExtensionInfo) env.getRuntimeInfo();
        }
        return null;
    }

    /**
     * 从 ExtensionGroup 中移除一个 Extension 实例，并触发其生命周期回调。
     *
     * @param extensionName 要移除的 Extension 的名称。
     */
    public void removeExtension(String extensionName) {
        ExtensionEnvImpl extensionEnv = extensions.remove(extensionName);
        ExtensionInfo extInfo = extensionInfos.remove(extensionName);

        if (extensionEnv != null && extensionEnv.getExtension() != null) {
            Extension extension = extensionEnv.getExtension();
            log.info("ExtensionGroup {}: Removing Extension {} from management. 实例环境: {}", name, extensionName,
                    extensionEnv.toString());
            try {
                // 调用生命周期回调
                extension.onStop(extensionEnv);
                extension.onDeinit(extensionEnv);
                extension.onDestroy(extensionEnv); // 最终销毁

                // 触发 ExtensionGroup 的 onDestroyExtensions 回调
                if (tenEnv != null) {
                    // 这里需要传递一个包含被销毁 Extension 的列表
                    tenEnv.postTask(() -> {
                        onDestroyExtensions(tenEnv, java.util.Collections.singletonList(extension));
                    });
                }
                log.info("ExtensionGroup {}: Extension {} lifecycle (onStop/onDeinit/destroy) completed.",
                        name, extensionName);
            } catch (Exception e) {
                log.error("ExtensionGroup {}: Error removing Extension {} or during lifecycle callbacks: {}",
                        name, extensionName, e.getMessage(), e);
            }
        } else {
            log.warn("ExtensionGroup {}: Extension {} not found for removal. 可能已被提前移除。", name, extensionName);
        }
    }
}