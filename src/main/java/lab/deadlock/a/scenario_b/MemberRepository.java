package lab.deadlock.a.scenario_b;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    /**
     * PK 경로 — 클러스터드 인덱스 한 단계로 row의 X 락만 잡는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Member findByIdForUpdate(@Param("id") Long id);

    /**
     * 보조 인덱스 경로 — {@code uk_member_email} entry에 X 락을 먼저 잡고,
     * 그 entry가 가리키는 PK row에도 X 락을 잡는다. 같은 row를 PK로 직접 잠그는
     * 경로와 락 획득 단계 수가 다르다는 점이 시나리오 (b)의 핵심이다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.email = :email")
    Member findByEmailForUpdate(@Param("email") String email);
}
