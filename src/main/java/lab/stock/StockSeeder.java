package lab.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockSeeder implements CommandLineRunner {

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void run(String... args) {
        stockRepository.truncate();
        stockRepository.saveAll(List.of(
                new Stock(100L),
                new Stock(50L),
                new Stock(150L),
                new Stock(200L)
        ));
        log.info("[SEED] stock 시드 완료: {} rows, ids={}",
                stockRepository.count(),
                stockRepository.findAll().stream().map(Stock::getId).toList());
    }
}
