package lab.deadlock.b.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final ProductRepository productRepository;

    public OrderService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 안티패턴 — PESSIMISTIC_READ(S-lock)로 조회한 뒤 dirty checking 으로
     * UPDATE 가 발생한다. 두 트랜잭션이 같은 row 의 S-lock 을 동시에 보유한
     * 채로 양쪽이 X-lock 으로 격상을 시도하면 데드락이 발생한다.
     */
    @Transactional
    public void order(Long productId, int quantity) {
        Product p = productRepository.findByIdForShare(productId).orElseThrow();
        if (p.getStock() < quantity) {
            throw new IllegalStateException("sold out");
        }
        p.decreaseStock(quantity); // UPDATE 발생 → X-lock 격상 요청
    }

    /**
     * 처방 1 — 처음부터 X-lock 으로 시작.
     */
    @Transactional
    public void orderWithWriteLock(Long productId, int quantity) {
        Product p = productRepository.findByIdForUpdate(productId).orElseThrow();
        if (p.getStock() < quantity) {
            throw new IllegalStateException("sold out");
        }
        p.decreaseStock(quantity);
    }

    /**
     * 처방 2 — 조회 단계를 없애고 원자적 UPDATE.
     */
    @Transactional
    public void orderSafe(Long productId, int quantity) {
        int updated = productRepository.decreaseStockAtomically(productId, quantity);
        if (updated == 0) {
            throw new IllegalStateException("sold out");
        }
    }
}
