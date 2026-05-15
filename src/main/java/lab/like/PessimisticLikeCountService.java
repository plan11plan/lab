package lab.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PessimisticLikeCountService {

    private final LikeCountRepository repo;

    @Transactional
    public void increment(Long productId) {
        LikeCountModel m = repo.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new IllegalArgumentException("like row not found: " + productId));
        m.increment();
    }
}
