package lab.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PessimisticStockService {

    private final StockRepository repo;

    @Transactional
    public void decrease(Long id, long quantity) {
        StockModel stock = repo.findByIdForUpdate(id)
            .orElseThrow(() -> new IllegalArgumentException("stock not found: " + id));
        stock.decrease(quantity);
    }
}
