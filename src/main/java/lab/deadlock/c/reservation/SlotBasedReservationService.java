package lab.deadlock.c.reservation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * §4.6 (b) 슬롯 단위 락 대안 — 시간을 30분 슬롯으로 끊고 PK 단건 락으로 직렬화한다.
 *
 * 핵심: 잠금 대상이 범위 SELECT가 아니라 PK 단건이므로 gap lock이 생성되지 않는다.
 * 빈 영역에 lock이 깔리지 않으니 Range/Gap Cycle 자체가 만들어지지 않는다.
 * 본 클래스는 본문 §4.6 (b)의 코드 시연 목적으로만 둔다.
 */
@Service
public class SlotBasedReservationService {

    private static final int SLOT_MINUTES = 30;

    private final ReservationSlotRepository slotRepository;

    public SlotBasedReservationService(ReservationSlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    @Transactional
    public void reserve(Long roomId, LocalDateTime start, LocalDateTime end) {
        List<Long> slotIds = slotIdsBetween(roomId, start, end);
        for (Long slotId : slotIds) {
            Optional<ReservationSlot> slot = slotRepository.findByIdForUpdate(slotId);
            if (slot.isPresent() && slot.get().isReserved()) {
                throw new ReservationConflictException("slot " + slotId + " is already reserved");
            }
        }
        for (Long slotId : slotIds) {
            slotRepository.save(new ReservationSlot(slotId, roomId, true));
        }
    }

    private static List<Long> slotIdsBetween(Long roomId, LocalDateTime start, LocalDateTime end) {
        List<Long> ids = new ArrayList<>();
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            ids.add(slotIdOf(roomId, cursor));
            cursor = cursor.plusMinutes(SLOT_MINUTES);
        }
        return ids;
    }

    private static long slotIdOf(Long roomId, LocalDateTime t) {
        long minutes = ChronoUnit.MINUTES.between(LocalDateTime.of(2026, 1, 1, 0, 0), t);
        long slot = minutes / SLOT_MINUTES;
        return roomId * 1_000_000_000L + slot;
    }
}
