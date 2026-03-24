package lab.learn.kafka.infra.idempotency;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 멱등 처리를 위한 이벤트 처리 이력 테이블.
 *
 * Consumer가 이벤트를 처리하기 전에 이 테이블을 조회하여
 * 동일한 eventId가 이미 처리되었는지 확인한다.
 *
 * at-least-once 전송 보장 환경에서 중복 메시지를 안전하게 무시할 수 있다.
 */
@Entity
@Table(name = "event_handled")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventHandled {

    @Id
    private String eventId;

    private String handlerName;

    private LocalDateTime handledAt;

    public static EventHandled of(String eventId, String handlerName) {
        EventHandled entity = new EventHandled();
        entity.eventId = eventId;
        entity.handlerName = handlerName;
        entity.handledAt = LocalDateTime.now();
        return entity;
    }
}
