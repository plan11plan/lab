package lab.deadlock.a.scenario_c;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 카테고리 A(c) — INSERT 시 여러 UNIQUE 인덱스 검사 순서.
 *
 * 같은 row 의 같은 unique 값으로 부딪히는 B(b)(§3.4) 와 다르게,
 * A(c) 는 두 트랜잭션이 *서로 다른* unique 값을 INSERT 하지만 *서로 다른*
 * UNIQUE 인덱스에서 *서로 다른* 기존 entry 와 충돌하면서 인덱스 entry 락
 * 획득 순서가 엇갈리는 케이스다.
 */
@Entity
@Table(
    name = "a_user_handle",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_handle_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_user_handle_phone", columnNames = "phone"),
    },
    indexes = {
        @Index(name = "ix_user_handle_email", columnList = "email"),
        @Index(name = "ix_user_handle_phone", columnList = "phone"),
    }
)
@Getter
@NoArgsConstructor
public class UserHandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(nullable = false, length = 30)
    private String phone;

    public UserHandle(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }
}
