package lab.deadlock.a.account;

import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountTransferService {

    private final AccountRepository accountRepository;

    public AccountTransferService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * 안티패턴 — 호출자가 넘긴 from→to 순서대로 락을 잡는다.
     * T1(transfer(1,2,...))과 T2(transfer(2,1,...))이 동시에 돌면
     * 락 획득 순서가 [1,2] vs [2,1]로 엇갈리면서 cycle이 닫힌다.
     */
    @Transactional
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        Account from = accountRepository.findByIdForUpdate(fromId); // 1단계 락
        Account to = accountRepository.findByIdForUpdate(toId);     // 2단계 락
        from.withdraw(amount);
        to.deposit(amount);
    }

    /**
     * 처방 — 항상 ID가 작은 계좌부터 잠근 뒤, 비즈니스 방향(from→to)으로 출금/입금.
     * 이체 방향이 어떻든 락 순서는 항상 [min, max]로 통일된다.
     */
    @Transactional
    public void transferSafe(Long fromId, Long toId, BigDecimal amount) {
        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);

        accountRepository.findByIdForUpdate(firstId);
        accountRepository.findByIdForUpdate(secondId);

        // 락은 정렬된 순서로 잡혔지만, 자금 이동은 호출자가 지정한 방향대로 진행한다.
        Account from = accountRepository.findByIdForUpdate(fromId);
        Account to = accountRepository.findByIdForUpdate(toId);
        from.withdraw(amount);
        to.deposit(amount);
    }
}
