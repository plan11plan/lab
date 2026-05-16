package lab.deadlock.c.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ReservationSlot s WHERE s.id = :id")
    Optional<ReservationSlot> findByIdForUpdate(@Param("id") Long id);
}
