package lab.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SerializableStockService {

    private final StockRepository stockRepository;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void updateThenSleep(String tag, Long id, long delta, long sleepMs) {
        log.info("[{}] phase=BEGIN id={} delta={}", tag, id, delta);
        Stock s = stockRepository.findById(id).orElseThrow();
        log.info("[{}] phase=AFTER_LOAD quantity={}", tag, s.getQuantity());
        s.decrease(delta);
        stockRepository.saveAndFlush(s);
        log.info("[{}] phase=AFTER_MUTATION newQuantity={}", tag, s.getQuantity());
        sleep(tag, sleepMs);
        log.info("[{}] phase=BEFORE_COMMIT", tag);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void update(String tag, Long id, long delta) {
        log.info("[{}] phase=BEGIN id={} delta={}", tag, id, delta);
        Stock s = stockRepository.findById(id).orElseThrow();
        log.info("[{}] phase=AFTER_LOAD quantity={}", tag, s.getQuantity());
        s.decrease(delta);
        stockRepository.saveAndFlush(s);
        log.info("[{}] phase=AFTER_MUTATION newQuantity={}", tag, s.getQuantity());
        log.info("[{}] phase=BEFORE_COMMIT", tag);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Long plainSelect(String tag, Long id) {
        log.info("[{}] phase=BEGIN id={}", tag, id);
        Stock s = stockRepository.findById(id).orElseThrow();
        log.info("[{}] phase=AFTER_LOAD quantity={}", tag, s.getQuantity());
        log.info("[{}] phase=BEFORE_COMMIT", tag);
        return s.getQuantity();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Long selectForUpdate(String tag, Long id) {
        log.info("[{}] phase=BEGIN id={}", tag, id);
        Stock s = stockRepository.findByIdForUpdate(id).orElseThrow();
        log.info("[{}] phase=AFTER_LOAD_FOR_UPDATE quantity={}", tag, s.getQuantity());
        log.info("[{}] phase=BEFORE_COMMIT", tag);
        return s.getQuantity();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Long readThenSleep(String tag, Long id, long sleepMs) {
        log.info("[{}] phase=BEGIN id={}", tag, id);
        Stock s = stockRepository.findById(id).orElseThrow();
        log.info("[{}] phase=AFTER_LOAD quantity={}", tag, s.getQuantity());
        sleep(tag, sleepMs);
        log.info("[{}] phase=BEFORE_COMMIT", tag);
        return s.getQuantity();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public int rangeSelectThenSleep(String tag, long lo, long hi, long sleepMs) {
        log.info("[{}] phase=BEGIN range=[{}, {}]", tag, lo, hi);
        List<Stock> rows = stockRepository.findInQuantityRange(lo, hi);
        log.info("[{}] phase=AFTER_LOAD count={} ids={}", tag, rows.size(),
                rows.stream().map(Stock::getId).toList());
        sleep(tag, sleepMs);
        log.info("[{}] phase=BEFORE_COMMIT", tag);
        return rows.size();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Long insert(String tag, long quantity) {
        log.info("[{}] phase=BEGIN insertQuantity={}", tag, quantity);
        Stock saved = stockRepository.saveAndFlush(new Stock(quantity));
        log.info("[{}] phase=AFTER_INSERT id={}", tag, saved.getId());
        log.info("[{}] phase=BEFORE_COMMIT", tag);
        return saved.getId();
    }

    private void sleep(String tag, long ms) {
        log.info("[{}] phase=BEFORE_SLEEP sleepMs={}", tag, ms);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[{}] phase=AFTER_SLEEP", tag);
    }
}
