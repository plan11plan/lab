package lab.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OptimisticStockService {

    private final StockRepository repo;

    @Transactional
    public void decrease(Long id, long quantity) {
        StockModel stock = repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("stock not found: " + id));
        stock.decrease(quantity);
        // @Version 충돌 시 트랜잭션 커밋 시점에 ObjectOptimisticLockingFailureException 발생
    }
}
