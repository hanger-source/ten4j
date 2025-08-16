package source.hanger.core.extension.bailian.realtime.events;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 表示 Realtime API 的 "connection.opened" 事件。
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@JsonTypeName("connection.opened")
public class ConnectionOpenedEvent extends RealtimeEvent {
}
