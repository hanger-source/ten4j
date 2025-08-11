package source.hanger.core.extension;

import java.util.List;

import source.hanger.core.graph.ExtensionGroupInfo;
import source.hanger.core.tenenv.TenEnv;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * `ExtensionGroup` 模拟 C 端 `ten_extension_group_t`，
 * 代表一组 Extension 的逻辑集合，并拥有自己的生命周期回调。
 */
@Getter
@Slf4j
public class ExtensionGroup {

    private final String name;
    private final ExtensionGroupInfo extensionGroupInfo;
    @Setter
    private TenEnv tenEnv; // ExtensionGroup 自己的 TenEnv 实例

    public ExtensionGroup(String name, ExtensionGroupInfo extensionGroupInfo) {
        this.name = name;
        this.extensionGroupInfo = extensionGroupInfo;
        log.info("ExtensionGroup {} created.", name);
    }

    // C 端 on_configure
    public void onConfigure(TenEnv env) {
        log.info("ExtensionGroup {}: onConfigure called.", name);
        // 实现 ExtensionGroup 自身的配置逻辑
        // 例如，根据 extensionGroupInfo.getProperty() 进行配置
    }

    // C 端 on_init
    public void onInit(TenEnv env) {
        log.info("ExtensionGroup {}: onInit called.", name);
        // 实现 ExtensionGroup 自身的初始化逻辑
    }

    // C 端 on_deinit
    public void onDeinit(TenEnv env) {
        log.info("ExtensionGroup {}: onDeinit called.", name);
        // 实现 ExtensionGroup 自身的去初始化逻辑
    }

    // C 端 on_create_extensions
    public void onCreateExtensions(TenEnv env) {
        log.info("ExtensionGroup {}: onCreateExtensions called. Extensions are about to be created.", name);
        // 在这里，ExtensionGroup 可以执行与创建 Extension 相关的逻辑
        // 例如，验证配置，准备资源等

        // 【新增】遍历并触发所有托管 Extension 的生命周期方法
        extensions.forEach((extId, extEnv) -> {
            try {
                log.info("ExtensionGroup {}: Triggering lifecycle for Extension {}.", name, extId);
                // 从 extEnv 获取 Extension 实例，并调用其生命周期方法
                Extension extension = extEnv.getExtension(); // 这里已经通过 extEnv 获取到 Extension 实例
                if (extension != null) {
                    extension.onConfigure(extEnv, extEnv.getExtensionInfo().getProperty());
                    extension.onInit(extEnv);
                    extension.onStart(extEnv);
                }
            } catch (Exception e) {
                log.error("ExtensionGroup {}: Error triggering Extension {} lifecycle: {}",
                        name, extId, e.getMessage(), e);
            }
        });
    }

    // C 端 on_destroy_extensions
    public void onDestroyExtensions(TenEnv env, List<Extension> extensionsToDestroy) {
        log.info("ExtensionGroup {}: onDestroyExtensions called. Extensions are about to be destroyed.", name);
        // 在这里，ExtensionGroup 可以执行与销毁 Extension 相关的逻辑
        // 例如，清理共享资源，通知其他组件等

        // 【新增】遍历并触发所有待销毁 Extension 的生命周期方法
        for (Extension extension : extensionsToDestroy) {
            // 通过 extensionId 从 managed extensions map 中获取 ExtensionEnvImpl
            String extensionId = extension.getExtensionName(); // 直接从 Extension 获取名称
            ExtensionEnvImpl extensionEnv = extensions.remove(extensionId);
            if (extensionEnv != null) {
                try {
                    log.info("ExtensionGroup {}: Triggering de-lifecycle for Extension {}.", name, extensionId);
                    extension.onStop(extensionEnv);
                    extension.onDeinit(extensionEnv);
                    extension.destroy(extensionEnv);
                } catch (Exception e) {
                    log.error("ExtensionGroup {}: Error triggering Extension {} de-lifecycle: {}",
                            name, extensionId, e.getMessage(), e);
                }
            } else {
                log.warn("ExtensionGroup {}: Extension {} not found in managed extensions for destruction.", name,
                        extensionId);
            }
        }
    }

}