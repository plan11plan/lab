package lab.deadlock.b.signup;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "b_user",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_b_user_email",    columnNames = "email"),
        @UniqueConstraint(name = "uk_b_user_nickname", columnNames = "nickname"),
        @UniqueConstraint(name = "uk_b_user_phone",    columnNames = "phone"),
    }
)
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String phone;

    public User(String email, String nickname, String phone) {
        this.email = email;
        this.nickname = nickname;
        this.phone = phone;
    }
}
