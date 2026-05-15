package lab.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AtomicIncrementLikeCountService {

    private final LikeCountRepository repo;

    @Transactional
    public void increment(Long productId) {
        int updated = repo.incrementAtomic(productId);
        if (updated == 0) {
            throw new IllegalStateException("like row not found: " + productId);
        }
    }
}
