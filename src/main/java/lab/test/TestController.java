package lab.test;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final TestRepository testRepository;

    @PostMapping
    public ResponseEntity<TestEntity> create(@RequestParam String name) {
        return ResponseEntity.ok(testRepository.save(new TestEntity(name)));
    }

    @GetMapping
    public ResponseEntity<List<TestEntity>> findAll() {
        return ResponseEntity.ok(testRepository.findAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        testRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
