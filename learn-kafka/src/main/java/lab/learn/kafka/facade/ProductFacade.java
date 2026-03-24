package lab.learn.kafka.facade;

import lab.learn.kafka.domain.event.ProductCreatedEvent;
import lab.learn.kafka.domain.event.ProductPriceChangedEvent;
import lab.learn.kafka.domain.model.Product;
import lab.learn.kafka.domain.repository.ProductRepository;
import lab.learn.kafka.infra.outbox.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품 도메인 오케스트레이션 파사드.
 *
 * Transactional Outbox 패턴 핵심:
 * - 도메인 상태 변경(Product 저장)과 이벤트 저장(Outbox)이 동일 트랜잭션에서 수행
 * - 트랜잭션 커밋 → 두 작업 모두 성공 보장
 * - 트랜잭션 롤백 → 두 작업 모두 취소 보장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductRepository productRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public String createProduct(String name, long price) {
        // 1. 도메인 상태 변경
        Product product = Product.create(name, price);
        productRepository.save(product);

        // 2. 같은 트랜잭션 내에서 Outbox에 이벤트 저장
        ProductCreatedEvent event = ProductCreatedEvent.of(product.getId(), name, price);
        outboxEventPublisher.save(event);

        log.info("상품 생성 + Outbox 이벤트 저장 완료 (동일 TX) - productId: {}", product.getId());
        return product.getId();
    }

    @Transactional
    public void changePrice(String productId, long newPrice) {
        // 1. 도메인 상태 변경
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다: " + productId));

        long oldPrice = product.changePrice(newPrice);
        productRepository.save(product);

        // 2. 같은 트랜잭션 내에서 Outbox에 이벤트 저장
        ProductPriceChangedEvent event = ProductPriceChangedEvent.of(
                productId, oldPrice, newPrice, product.getVersion());
        outboxEventPublisher.save(event);

        log.info("가격 변경 + Outbox 이벤트 저장 완료 (동일 TX) - productId: {}, {} → {}",
                productId, oldPrice, newPrice);
    }
}
