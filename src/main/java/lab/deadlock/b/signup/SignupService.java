package lab.deadlock.b.signup;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignupService {

    private final UserRepository userRepository;

    public SignupService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 안티패턴 — 단순 INSERT.
     * UNIQUE 인덱스가 여러 개일 때 두 트랜잭션이 같은 unique 값으로 충돌하면
     * 묵시적 S-lock 이 공존하다가 양쪽이 X-lock 으로 격상되며 데드락이 발생한다.
     */
    @Transactional
    public void register(SignupForm form) {
        userRepository.save(new User(form.email(), form.nickname(), form.phone()));
    }

    /**
     * 처방 — retry-on-deadlock 후에도 unique violation 이 남으면 의미적 에러로 변환.
     */
    public void registerSafe(SignupForm form) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                register(form);
                return;
            } catch (DeadlockLoserDataAccessException e) {
                if (attempts >= 3) {
                    throw new DuplicateSignupException("이미 사용 중인 정보입니다");
                }
            } catch (DataIntegrityViolationException e) {
                throw new DuplicateSignupException("이미 사용 중인 정보입니다");
            }
        }
    }
}
