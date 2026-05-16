package lab.deadlock.a.scenario_c;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserHandleRepository extends JpaRepository<UserHandle, Long> {

    /** UNIQUE 인덱스 entry 자체에 X-lock 을 미리 잡기 위한 보조 조회 — 테스트용. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserHandle u WHERE u.email = :email")
    Optional<UserHandle> findByEmailForUpdate(@Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserHandle u WHERE u.phone = :phone")
    Optional<UserHandle> findByPhoneForUpdate(@Param("phone") String phone);
}
