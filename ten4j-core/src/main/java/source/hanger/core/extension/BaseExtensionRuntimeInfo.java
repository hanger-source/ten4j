package source.hanger.core.extension;

import source.hanger.core.message.Location;

import java.util.Map;

/**
 * 运行时 Extension 和 ExtensionGroup 信息的共同基础接口。
 * 定义了获取实例名称、addon 名称、位置和属性的通用方法。
 */
public interface BaseExtensionRuntimeInfo {
    /**
     * 获取实例的名称（例如 Extension 的 instanceName 或 ExtensionGroup 的 instanceName）。
     *
     * @return 实例的名称。
     */
    String getInstanceName();

    /**
     * 获取关联的 Addon 名称。
     *
     * @return Addon 名称。
     */
    String getAddonName();

    /**
     * 获取实例的位置信息。
     *
     * @return Location 对象。
     */
    Location getLoc();

    /**
     * 获取实例的配置属性。
     *
     * @return 属性的 Map。
     */
    Map<String, Object> getProperty();
}