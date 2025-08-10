package source.hanger.core.util;

import java.lang.reflect.Constructor;

/**
 * 提供反射工具方法。
 */
public class ReflectionUtils {

    private ReflectionUtils() {
        // Utility class
    }

    /**
     * 通过无参构造函数创建类的新实例。
     *
     * @param clazz 要实例化的 Class 对象。
     * @param <T>   实例的类型。
     * @return 新创建的实例。
     * @throws RuntimeException 如果实例化失败。
     */
    public static <T> T newInstance(Class<T> clazz) {
        try {
            Constructor<T> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true); // 允许访问私有构造函数
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create new instance of class " + clazz.getName(), e);
        }
    }
}