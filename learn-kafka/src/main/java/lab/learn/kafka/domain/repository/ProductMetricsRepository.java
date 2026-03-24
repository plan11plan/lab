package lab.learn.kafka.domain.repository;

import lab.learn.kafka.domain.model.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsRepository extends JpaRepository<ProductMetrics, String> {
}
