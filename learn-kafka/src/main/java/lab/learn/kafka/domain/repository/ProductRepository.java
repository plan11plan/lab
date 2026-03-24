package lab.learn.kafka.domain.repository;

import lab.learn.kafka.domain.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
}
