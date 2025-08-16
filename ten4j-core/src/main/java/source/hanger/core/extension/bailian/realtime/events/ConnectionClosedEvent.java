package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 连接关闭事件。
 * 这是一个内部事件，用于通知客户端连接已关闭。
 */
@Getter
@Setter
@ToString(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@JsonTypeName("connection.closed")
public class ConnectionClosedEvent extends RealtimeEvent {

    private int code;
    private String reason;
}
