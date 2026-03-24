package lab.learn.kafka.infra.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventHandledRepository extends JpaRepository<EventHandled, String> {
}
