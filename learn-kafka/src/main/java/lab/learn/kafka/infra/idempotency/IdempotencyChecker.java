package lab.learn.kafka.infra.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 이벤트 멱등 처리 검증기.
 *
 * event_handled 테이블을 통해 이미 처리된 이벤트를 식별하고,
 * 중복 처리를 방지한다.
 *
 * 사용 흐름:
 * 1. Consumer가 메시지 수신
 * 2. isAlreadyHandled(eventId)로 중복 확인
 * 3. 중복이 아니면 비즈니스 로직 수행
 * 4. markAsHandled(eventId)로 처리 완료 기록
 * 5. manual ack 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyChecker {

    private final EventHandledRepository eventHandledRepository;

    public boolean isAlreadyHandled(String eventId) {
        boolean exists = eventHandledRepository.existsById(eventId);
        if (exists) {
            log.info("이미 처리된 이벤트 감지 (멱등 처리) - eventId: {}", eventId);
        }
        return exists;
    }

    public void markAsHandled(String eventId, String handlerName) {
        eventHandledRepository.save(EventHandled.of(eventId, handlerName));
        log.info("이벤트 처리 완료 기록 - eventId: {}, handler: {}", eventId, handlerName);
    }
}
