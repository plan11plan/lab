package lab.deadlock.a.scenario_c;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserHandleService {

    private final UserHandleRepository repository;

    public UserHandleService(UserHandleRepository repository) {
        this.repository = repository;
    }

    /**
     * 안티패턴 — 단순 INSERT.
     *
     * 한 row 의 INSERT 가 두 개의 UNIQUE 인덱스(email, phone) entry 를 차례로 검사하면서
     * 각각의 충돌 row 에 묵시적 락을 잡는다. 두 트랜잭션이 *서로 다른* 인덱스에서 *서로 다른*
     * 기존 row 를 건드리면, 인덱스 entry 락 획득 순서가 엇갈려 cycle 이 닫힐 수 있다.
     */
    @Transactional
    public void register(String email, String phone) {
        repository.save(new UserHandle(email, phone));
    }

    /**
     * 처방 1 — 충돌이 본래 비즈니스 의미("이미 사용 중")인 케이스로 변환.
     * 데드락이 잡히면 한 번 재시도하고, 이후에도 unique violation 이면 의미적 에러로 흘려보낸다.
     */
    public void registerWithRetry(String email, String phone) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                register(email, phone);
                return;
            } catch (DeadlockLoserDataAccessException e) {
                if (attempts >= 3) {
                    throw new DuplicateHandleException("이미 사용 중인 정보입니다");
                }
            } catch (DataIntegrityViolationException e) {
                throw new DuplicateHandleException("이미 사용 중인 정보입니다");
            }
        }
    }
}
