package lab.deadlock.c.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 안티패턴 시연용 서비스.
 * overlap 검사 후 INSERT — overlap SELECT가 잡는 gap/next-key lock과
 * 후속 INSERT의 insert intention lock이 서로 침범하면서 cycle을 만든다.
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;

    public ReservationService(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public Reservation reserve(Long roomId, LocalDateTime start, LocalDateTime end) {
        List<Reservation> conflicts =
            reservationRepository.findOverlappingForUpdate(roomId, start, end);
        if (!conflicts.isEmpty()) {
            throw new ReservationConflictException(
                "room " + roomId + " is already reserved between " + start + " and " + end);
        }
        return reservationRepository.save(new Reservation(roomId, start, end));
    }
}
