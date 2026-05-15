package lab.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtomicUpdateStockService {

    private final StockRepository repo;

    @Transactional
    public void decrease(Long id, long quantity) {
        int updated = repo.decreaseAtomic(id, quantity);
        if (updated == 0) {
            throw new IllegalStateException("stock not enough or not found: " + id);
        }
    }
}
