package source.hanger.core.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StatusCode {
    INVALID(-1),
    OK(0),
    ERROR(1);

    private final int value;

    StatusCode(int value) {
        this.value = value;
    }

    @JsonValue
    public int getValue() {
        return value;
    }

    @JsonCreator
    public static StatusCode fromValue(int value) {
        for (StatusCode code : StatusCode.values()) {
            if (code.value == value) {
                return code;
            }
        }
        return INVALID;
    }
}