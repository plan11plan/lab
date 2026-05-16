package lab.deadlock.a.scenario_b;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시나리오 (b) — 같은 row, 다른 인덱스 경로용 엔티티.
 *
 * {@code email}에 UNIQUE 보조 인덱스를 명시적으로 정의해, 보조 인덱스 entry → PK row
 * 두 단계 락이 잡히는 경로를 만든다. PK({@code id})로 직접 잠그는 경로와 비교될 때
 * cross-lock이 닫히는 비대칭이 드러난다.
 */
@Entity
@Table(
        name = "member",
        indexes = @Index(name = "uk_member_email", columnList = "email", unique = true)
)
@Getter
@NoArgsConstructor
public class Member {

    @Id
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public Member(Long id, String email) {
        this.id = id;
        this.email = email;
    }

    public void touchLogin(Instant now) {
        this.lastLoginAt = now;
    }
}
