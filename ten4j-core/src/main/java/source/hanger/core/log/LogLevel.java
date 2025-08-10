package source.hanger.core.log;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 自定义日志级别枚举，与 C 端的日志级别对齐。
 */
public enum LogLevel {
    DEBUG(0),
    INFO(1),
    WARN(2),
    ERROR(3),
    FATAL(4);

    private final int level;

    LogLevel(int level) {
        this.level = level;
    }

    @JsonValue
    public int getLevel() {
        return level;
    }

    @JsonCreator
    public static LogLevel fromLevel(int level) {
        for (LogLevel l : LogLevel.values()) {
            if (l.level == level) {
                return l;
            }
        }
        throw new IllegalArgumentException("Unknown log level: " + level);
    }
}