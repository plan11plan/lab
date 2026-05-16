package lab.deadlock.c.reservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * §4.6 (b) 슬롯 단위 락 대안용 엔티티.
 * 시간을 30분 슬롯으로 끊고 슬롯 row를 PK 단건으로 잡으면 gap lock이 생성되지 않는다.
 */
@Entity
@Table(name = "reservation_slots")
public class ReservationSlot {

    @Id
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "reserved", nullable = false)
    private boolean reserved;

    protected ReservationSlot() {
    }

    public ReservationSlot(Long id, Long roomId, boolean reserved) {
        this.id = id;
        this.roomId = roomId;
        this.reserved = reserved;
    }

    public Long getId() {
        return id;
    }

    public boolean isReserved() {
        return reserved;
    }
}
