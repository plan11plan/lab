package lab.like;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LikeCountRepository extends JpaRepository<LikeCountModel, Long> {

    Optional<LikeCountModel> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LikeCountModel l WHERE l.productId = :productId")
    Optional<LikeCountModel> findByProductIdForUpdate(@Param("productId") Long productId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE LikeCountModel l SET l.count = l.count + 1 WHERE l.productId = :productId")
    int incrementAtomic(@Param("productId") Long productId);
}
