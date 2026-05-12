package lab.stock;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pessimistic")
@RequiredArgsConstructor
public class PessimisticStockController {

    private final PessimisticStockService service;

    @PostMapping("/update-sleep")
    public ResponseEntity<Void> updateSleep(@RequestParam(defaultValue = "A") String tag,
                                            @RequestParam(defaultValue = "1") Long id,
                                            @RequestParam(defaultValue = "1") long delta,
                                            @RequestParam(defaultValue = "5000") long sleep) {
        service.updateThenSleep("TX-" + tag, id, delta, sleep);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/update")
    public ResponseEntity<Void> update(@RequestParam(defaultValue = "B") String tag,
                                       @RequestParam(defaultValue = "1") Long id,
                                       @RequestParam(defaultValue = "1") long delta) {
        service.update("TX-" + tag, id, delta);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/select")
    public ResponseEntity<Long> select(@RequestParam(defaultValue = "B") String tag,
                                       @RequestParam(defaultValue = "1") Long id) {
        return ResponseEntity.ok(service.plainSelect("TX-" + tag, id));
    }

    @GetMapping("/select-for-update")
    public ResponseEntity<Long> selectForUpdate(@RequestParam(defaultValue = "B") String tag,
                                                @RequestParam(defaultValue = "1") Long id) {
        return ResponseEntity.ok(service.selectForUpdate("TX-" + tag, id));
    }

    @GetMapping("/select-sleep")
    public ResponseEntity<Long> selectSleep(@RequestParam(defaultValue = "A") String tag,
                                            @RequestParam(defaultValue = "1") Long id,
                                            @RequestParam(defaultValue = "5000") long sleep) {
        return ResponseEntity.ok(service.readThenSleep("TX-" + tag, id, sleep));
    }

    @PostMapping("/lock-single-sleep")
    public ResponseEntity<Long> lockSingleSleep(@RequestParam(defaultValue = "A") String tag,
                                                @RequestParam(defaultValue = "1") Long id,
                                                @RequestParam(defaultValue = "5000") long sleep) {
        return ResponseEntity.ok(service.lockSingleRowThenSleep("TX-" + tag, id, sleep));
    }

    @GetMapping("/range-select-sleep")
    public ResponseEntity<Integer> rangeSelectSleep(@RequestParam(defaultValue = "A") String tag,
                                                    @RequestParam(defaultValue = "40") long lo,
                                                    @RequestParam(defaultValue = "160") long hi,
                                                    @RequestParam(defaultValue = "5000") long sleep) {
        return ResponseEntity.ok(service.rangeSelectForUpdateThenSleep("TX-" + tag, lo, hi, sleep));
    }

    @PostMapping("/insert")
    public ResponseEntity<Long> insert(@RequestParam(defaultValue = "B") String tag,
                                       @RequestParam(defaultValue = "120") long quantity) {
        return ResponseEntity.ok(service.insert("TX-" + tag, quantity));
    }
}
