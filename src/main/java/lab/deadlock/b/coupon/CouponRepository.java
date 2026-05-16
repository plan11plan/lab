package lab.deadlock.b.coupon;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<CouponCode, String> {

    /**
     * 안티패턴 — UPSERT.
     * MySQL InnoDB 에서 INSERT ... ON DUPLICATE KEY UPDATE 는 PK 충돌을 발견할 때
     * 충돌 row 에 묵시적 S-lock 을 깐 뒤 X-lock 으로 격상한다.
     */
    @Modifying
    @Query(value = """
        INSERT INTO b_coupon_code(code, claimed_by, claimed_at)
        VALUES (:code, :userId, NOW())
        ON DUPLICATE KEY UPDATE claimed_by = :userId, claimed_at = NOW()
        """, nativeQuery = true)
    void claim(@Param("code") String code, @Param("userId") Long userId);

    /**
     * 처방 — 명시적 X-lock 으로 조회 → 분기.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CouponCode c WHERE c.code = :code")
    Optional<CouponCode> findByCodeForUpdate(@Param("code") String code);
}
