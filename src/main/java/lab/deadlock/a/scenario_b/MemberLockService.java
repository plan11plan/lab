package lab.deadlock.a.scenario_b;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberLockService {

    private final MemberRepository memberRepository;

    public MemberLockService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 안티패턴 — 호출자가 넘긴 두 키(하나는 email, 하나는 id)로 차례로 잠근다.
     * 호출 시점마다 어떤 키가 어느 사용자에 매핑되는지가 달라지므로,
     * 보조 인덱스 entry 락 + PK row 락 vs PK row 락 두 경로가 동일 row에 대해
     * 반대 방향으로 닫혀 cycle이 만들어진다.
     */
    @Transactional
    public void lockTwoMembers(String firstEmail, Long secondId) {
        memberRepository.findByEmailForUpdate(firstEmail); // 보조 인덱스 → PK 두 단계
        memberRepository.findByIdForUpdate(secondId);      // PK 한 단계
    }

    /**
     * 처방 — PK ID 정렬 후 PK 경로로 통일해서 잠근다.
     * 보조 인덱스 entry 락 단계가 빠지므로 두 트랜잭션의 락 획득 모양이 동일해진다.
     */
    @Transactional
    public void lockTwoMembersSafe(Long firstId, Long secondId) {
        Long lo = Math.min(firstId, secondId);
        Long hi = Math.max(firstId, secondId);
        memberRepository.findByIdForUpdate(lo);
        memberRepository.findByIdForUpdate(hi);
    }
}
