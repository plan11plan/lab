# Lost Update를 막는 두 가지 방법 — SERIALIZABLE vs PESSIMISTIC_WRITE 실측

## 서론

재고, 좋아요, 쿠폰 같이 같은 row의 카운트를 갱신하는 로직에서는 같은 동시성 문제가 반복적으로 등장하는데, 이를 흔히 **Lost Update**라고 부른다. N개의 트랜잭션이 같은 row를 동시에 수정할 때 마지막에 커밋된 값만 반영되고 나머지 변경이 묻히는 현상을 가리킨다.

트랜잭션 ACID와 격리 수준을 학습하면서 SERIALIZABLE이 다른 트랜잭션의 읽기·수정을 모두 막는 강력한 방법이지만 그만큼 동시성을 떨어뜨린다는 점을 알게 됐다. 한편 비관락(`SELECT ... FOR UPDATE`)도 다른 트랜잭션의 읽기·수정을 막는다는 비슷한 설명이 따라붙는데, 둘이 정확히 어디서 어떻게 다른지가 궁금해 직접 실측해봤다.

다루는 두 후보를 Spring 표기로 옮기면 격리 수준 선언과 비관락 어노테이션 두 가지로 정리된다.

- 격리 수준을 SERIALIZABLE로 올린다 — `@Transactional(isolation = Isolation.SERIALIZABLE)`
- 비관적 락 — `@Lock(LockModeType.PESSIMISTIC_WRITE)` (= `SELECT ... FOR UPDATE`)

### 결론부터

실측한 결론을 먼저 짚으면, 두 전략은 *수정* 시 X 락을 잡고 외부 트랜잭션의 S·X 락을 막는다는 점에서 구조적으로 같다. 차이는 *읽기*에서 MVCC read view를 생성하느냐에 있다. 비관락은 격리 수준이 REPEATABLE READ(InnoDB 기본값)이라 read view를 만들기 때문에 다른 트랜잭션의 일반 SELECT가 락 경합 없이 snapshot으로 통과하는 반면, SERIALIZABLE은 read view를 만들지 않고 일반 SELECT조차 자동으로 S 락을 잡는 *잠금 읽기*로 변환하므로 같은 row에 X 락이 있으면 대기한다. 결국 두 전략의 차이는 *읽기 락*을 어떻게 다루느냐로 모인다.

### 비교

|  | **비관락 (`SELECT FOR UPDATE`)** | **SERIALIZABLE** |
| --- | --- | --- |
| 격리 수준 | REPEATABLE READ (InnoDB 기본값) | SERIALIZABLE |
| MVCC read view 생성 | O — 첫 SELECT 시점에 생성 | X — 모든 읽기가 락 기반 |
| MVCC 사용 | O — 일반 SELECT는 snapshot으로 읽음 | X — 일반 SELECT → `FOR SHARE`로 변환 |
| S·X 락 잠금 시점 | `SELECT FOR UPDATE` 실행 시 X 락 | 일반 SELECT 실행 시 자동 S 락 / `UPDATE`·`DELETE`·`FOR UPDATE`는 X 락 |
| S·X 락 적용 범위 | `FOR UPDATE`를 명시한 행만 | 트랜잭션 안에서 읽은 행 전부 |
| S·X 락 해제 시점 | 트랜잭션 커밋 / 롤백 | 트랜잭션 커밋 / 롤백 |
| 외부의 일반 SELECT | 허용 (MVCC snapshot, 락 없음) | 차단 (`FOR SHARE`로 변환되어 대기) |

격리 수준별로 일반 SELECT가 어떤 경로로 처리되는지도 함께 정리해 둔다.

| 격리 수준 | 일반 SELECT 방식 | S 락 획득 |
| --- | --- | --- |
| READ UNCOMMITTED | 최신 버전 직접 읽기 (dirty read) | X |
| READ COMMITTED | MVCC — statement마다 새 read view | X |
| REPEATABLE READ | MVCC — 트랜잭션 첫 읽기에 read view 고정 | X |
| SERIALIZABLE | `FOR SHARE`로 자동 변환 | O |

S 락을 잡는 격리 수준은 SERIALIZABLE 하나뿐이고, 나머지 셋은 모두 MVCC 계열(또는 dirty read)이라 읽기 경로에서의 락 경합 자체가 발생하지 않는다.

### 본문에서 풀 내용

위 결론을 만든 시나리오들을 실측 응답 시간과 `data_locks` 출력으로 풀어간다.

- **차이 1** — SERIALIZABLE이 plain SELECT까지 자동으로 S 락으로 격상하는 모습
- **차이 2** — 비관락 전략에서 *read 경로*에 잠금을 빠뜨렸을 때 Lost Update가 발생하는 흐름
- **차이 3** — next-key 락과 `supremum pseudo-record`가 등장하는 범위 보호

끝에서는 두 전략의 결과가 같았던 세 시나리오를 함께 짚어 "두 전략이 똑같다"는 인상의 출처도 확인한다.

---

## 사전 개념

본문에서 반복적으로 등장하는 용어를 미리 정리한다.

### 1. 격리 수준

여러 트랜잭션이 같은 row를 동시에 읽거나 쓸 때 무엇을 허용하고 무엇을 막을지를 결정한다. SQL 표준은 네 단계를 정의한다.

| 격리 수준 | 정의 |
|---|---|
| READ UNCOMMITTED | 다른 트랜잭션의 커밋 전 변경분까지 읽을 수 있다 |
| READ COMMITTED | 커밋된 값만 읽되, 같은 트랜잭션 안에서도 매 SELECT의 결과가 바뀔 수 있다 |
| REPEATABLE READ | 트랜잭션 시작 시점 snapshot을 고정해, 같은 row를 다시 읽으면 같은 값이 나온다 |
| SERIALIZABLE | 트랜잭션들을 순차 실행한 것과 같은 결과를 보장한다 |

InnoDB의 기본은 **REPEATABLE READ**, 한 단계 위가 **SERIALIZABLE**이다. 본 글의 두 전략 중 PESSIMISTIC_WRITE 컬럼은 기본값에서 동작하고, SERIALIZABLE 컬럼은 한 단계 위로 올려둔 상태다.

### 2. S 락 / X 락

InnoDB가 row 단위로 거는 두 종류의 락.

| 항목 | S 락 (공유) | X 락 (배타) |
|---|---|---|
| 동시 보유 | 여러 트랜잭션 가능 | 한 트랜잭션만 |
| 누가 요청 | `SELECT ... LOCK IN SHARE MODE`, SERIALIZABLE의 plain SELECT | `UPDATE`, `DELETE`, `SELECT ... FOR UPDATE` |
| S 요청 시 | 통과 | 막음 |
| X 요청 시 | 막음 | 막음 |

호환 규칙은 단순하다.

- **S–S**: 호환 (둘 다 통과)
- **S–X**: 충돌 (대기)
- **X–X**: 충돌 (대기)

### 3. MVCC

InnoDB는 row를 UPDATE 할 때 변경 전 값을 undo log에 남겨둔다. 다른 트랜잭션의 plain SELECT는 자기 트랜잭션 시작 시점의 read view를 기준으로 undo log에서 적절한 과거 버전을 골라 돌려준다.

결과로 세 가지가 따라온다.

- plain SELECT는 X 락이 잡힌 row를 대상으로도 잠금 대기 없이 즉시 응답한다.
- 응답값은 *현재 row의 최신 값*이 아니라, 자기 트랜잭션 시작 시점의 snapshot이다.
- `SELECT ... FOR UPDATE` 또는 SERIALIZABLE 안의 plain SELECT는 MVCC 경로가 아니라 락 경로를 탄다.

**MVCC vs 잠금 경로** — 같은 SELECT 한 줄이라도 어느 쪽을 타는지는 *격리 수준 + 쿼리 종류*의 조합으로 결정된다.

| 조합 | 경로 | 락 | 읽는 값 |
|---|---|---|---|
| REPEATABLE READ + plain SELECT | **MVCC** | 없음 | snapshot |
| REPEATABLE READ + `SELECT FOR UPDATE` | 잠금 | X | 최신 커밋값 |
| SERIALIZABLE + plain SELECT | 잠금 (자동 격상) | S | 최신 커밋값 |
| SERIALIZABLE + `SELECT FOR UPDATE` | 잠금 | X | 최신 커밋값 |
| 모든 격리 + `UPDATE` / `DELETE` | 잠금 | X | — (쓰기) |

표에서 보이듯 두 전략 모두 *쓰기*에는 X 락을 잡지만, *읽기*에서 SERIALIZABLE은 plain SELECT까지 자동으로 S 락(잠금 경로)으로 격상시키는 반면, PESSIMISTIC_WRITE는 잠금 힌트가 없는 SELECT를 MVCC 경로로 통과시키고 `FOR UPDATE`가 붙은 읽기에만 X 락을 부여한다. 본 글에서 보게 될 응답 시간 차이는 대부분 이 *읽기 경로의 분기*에서 비롯된다.

### 4. Record / Gap / Next-Key Lock

InnoDB row-level 락은 잠금 범위에 따라 셋으로 갈린다.

| 종류 | 잠그는 대상 | 무엇을 막나 |
|---|---|---|
| Record lock | 인덱스 record 한 줄 | 같은 record의 다른 락 요청 |
| Gap lock | 인덱스 record 사이 빈 공간 | 그 공간으로의 INSERT |
| Next-key lock | record + 그 앞 gap (결합) | 둘 다 |

`data_locks`의 `lock_mode` 표기로 구분된다.

- `S,REC_NOT_GAP` / `X,REC_NOT_GAP` — record lock만 (gap 없음)
- `S` / `X` — next-key lock (record + gap)
- `S,GAP` / `X,GAP` — gap lock만

### 5. `data_locks` 읽는 법

`performance_schema.data_locks`는 InnoDB가 들고 있는 모든 락 객체의 현재 시점 스냅샷이다. 한 트랜잭션이 락을 여러 개 동시 보유하므로 같은 `trx_id`가 여러 행으로 나온다.

각 컬럼이 의미하는 바는 다음과 같다.

| 컬럼 | 뜻 |
|---|---|
| `trx_id` | 락을 보유한 트랜잭션 ID |
| `object_name` / `index_name` | 테이블, 인덱스. `index_name=NULL`이면 테이블 레벨 |
| `lock_type` | `TABLE` 또는 `RECORD` |
| `lock_mode` | 락 종류·범위 (위의 표기) |
| `lock_status` | `GRANTED` 또는 `WAITING` |
| `lock_data` | RECORD 락이면 PK 값. TABLE이면 NULL |

InnoDB는 **계층적 잠금**을 쓴다. row에 락을 걸 때 테이블에 *의도 락(intention lock)* 을 함께 박아둔다.

- `IS` — S 락을 row에 걸 *예정*
- `IX` — X 락을 row에 걸 *예정*

그래서 `UPDATE row 1` 한 번에도 `TABLE / IX` + `RECORD / X,REC_NOT_GAP / lock_data=1` 두 행이 나온다. 의도 락이 따로 있는 이유는 `LOCK TABLES`, `ALTER TABLE` 같은 테이블 레벨 락 요청의 빠른 충돌 판정을 위해서다.

---

## 실험 환경

- **환경**: Spring Boot 4.0.3 + MySQL 8.0(InnoDB), 단일 머신
- **초기 데이터**: 매 시나리오 시작 전 `stock` 테이블을 비우고 4개 row를 시드한다.

  | `id` | `quantity` |
  |---|---|
  | 1 | 100 |
  | 2 | 50  |
  | 3 | 150 |
  | 4 | 200 |

  row 1은 차이 1·2에서 두 트랜잭션이 부딪히는 동시 수정 대상이 되고, row 2~4는 차이 3의 범위 SELECT(`WHERE quantity BETWEEN 40 AND 160`)에 함께 매칭되어 next-key 락의 시연 대상이 된다.

- **측정 방법**: Tx A를 백그라운드로 발사해 트랜잭션 안에서 `Thread.sleep(5000)`으로 5초 잡아둔다. 0.5초 뒤 Tx B를 같은 row에 발사한 뒤 응답 시간(`time_total`)을 본다.
  - 4초대 → 락 대기
  - 0.1초 미만 → 즉시 응답

도메인 엔티티 한 개:

```java
@Entity
@Table(name = "stock")
public class Stock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long quantity;

    public void decrease(long amount) {
        if (this.quantity < amount) {
            throw new IllegalStateException("재고 부족: 현재=" + this.quantity + ", 차감=" + amount);
        }
        this.quantity -= amount;
    }
}
```

Repository는 잠금 없는 조회와 `FOR UPDATE` 조회를 둘 다 노출한다.

```java
public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findById(Long id);                       // 잠금 없음

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.id = :id")
    Optional<Stock> findByIdForUpdate(@Param("id") Long id);  // SELECT ... FOR UPDATE

    @Query("select s from Stock s where s.quantity between :lo and :hi order by s.id")
    List<Stock> findInQuantityRange(@Param("lo") long lo, @Param("hi") long hi);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.quantity between :lo and :hi order by s.id")
    List<Stock> findInQuantityRangeForUpdate(@Param("lo") long lo, @Param("hi") long hi);
}
```

서비스는 두 컬럼으로 갈린다.

**SERIALIZABLE 컬럼** — 격리 수준을 한 단계 올리고 모든 조회를 `findById`로 한다. 잠금 힌트가 없어도 SERIALIZABLE에서는 plain SELECT가 자동으로 S 락 요청으로 격상된다.

```java
@Service
@RequiredArgsConstructor
public class SerializableStockService {

    private final StockRepository stockRepository;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void updateThenSleep(Long id, long delta, long sleepMs) {
        Stock s = stockRepository.findById(id).orElseThrow();
        s.decrease(delta);
        stockRepository.saveAndFlush(s);
        Thread.sleep(sleepMs);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Long plainSelect(Long id) {
        return stockRepository.findById(id).orElseThrow().getQuantity();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public Long readThenSleep(Long id, long sleepMs) {
        Stock s = stockRepository.findById(id).orElseThrow();
        Thread.sleep(sleepMs);
        return s.getQuantity();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void update(Long id, long delta) {
        Stock s = stockRepository.findById(id).orElseThrow();
        s.decrease(delta);
        stockRepository.saveAndFlush(s);
    }
    // range select / insert 등도 모두 isolation=SERIALIZABLE
}
```

**PESSIMISTIC_WRITE 컬럼** — 격리 수준을 기본(REPEATABLE READ)으로 두고, 비관락의 정상 사용 방식대로 *쓰기 경로*에서만 `findByIdForUpdate`로 X 락을 잡는다.

```java
@Service
@RequiredArgsConstructor
public class PessimisticStockService {

    private final StockRepository stockRepository;

    @Transactional
    public void updateThenSleep(Long id, long delta, long sleepMs) {
        Stock s = stockRepository.findByIdForUpdate(id).orElseThrow();  // 명시적 X 락
        s.decrease(delta);
        stockRepository.saveAndFlush(s);
        Thread.sleep(sleepMs);
    }

    @Transactional
    public void update(Long id, long delta) {
        Stock s = stockRepository.findByIdForUpdate(id).orElseThrow();  // 명시적 X 락
        s.decrease(delta);
        stockRepository.saveAndFlush(s);
    }

    @Transactional
    public Long plainSelect(Long id) {
        return stockRepository.findById(id).orElseThrow().getQuantity(); // 잠금 없음 (MVCC 경로)
    }

    @Transactional
    public Long readThenSleep(Long id, long sleepMs) {
        Stock s = stockRepository.findById(id).orElseThrow();             // 잠금 없음 (의도적)
        Thread.sleep(sleepMs);
        return s.getQuantity();
    }
    // ...
}
```

`PessimisticStockService.readThenSleep`은 비관락 컬럼인데도 의도적으로 `findById`를 쓴다. 이는 차이 2에서 다룰 **부정 시연**용 — "비관락 전략을 채택했지만 read 경로에 `FOR UPDATE`를 빠뜨린 경우"를 보여주기 위해서다.

> **확인**: 이 두 서비스가 실제로 다른 격리 수준으로 동작하는지는 Tx A의 sleep 도중 `information_schema.INNODB_TRX.trx_isolation_level`을 조회하면 그대로 보인다 — SERIALIZABLE 컬럼은 `SERIALIZABLE`, PESSIMISTIC_WRITE 컬럼은 `REPEATABLE READ`.

---

## 차이 1 — 비관 락은 plain SELECT를 막지 않는다

**시나리오**: Tx A가 UPDATE 후 5초 sleep으로 트랜잭션을 잡고 있을 때, Tx B가 잠금 힌트 없는 `findById`로 단순 조회를 한다.

### SERIALIZABLE 컬럼

**호출되는 메서드**

```java
// Tx A
SerializableStockService.updateThenSleep(1L, 1, 5000);  // findById → decrease → save → sleep 5s
// Tx B  (A보다 0.5초 늦게)
SerializableStockService.plainSelect(1L);               // findById만
```

**결과**: Tx B 응답 시간 **4.556s**, 값 `99` (Tx A 커밋 후 값).

**락 상태** — Tx A의 sleep 도중 `data_locks` 출력:

```
+--------+-------------+------------+-----------+---------------+-------------+-----------+
| trx_id | object_name | index_name | lock_type | lock_mode     | lock_status | lock_data |
+--------+-------------+------------+-----------+---------------+-------------+-----------+
|   2366 | stock       | NULL       | TABLE     | IX            | GRANTED     | NULL      |
|   2366 | stock       | NULL       | TABLE     | IS            | GRANTED     | NULL      |
|   2366 | stock       | PRIMARY    | RECORD    | S,REC_NOT_GAP | GRANTED     | 1         |
|   2366 | stock       | PRIMARY    | RECORD    | X,REC_NOT_GAP | GRANTED     | 1         |
+--------+-------------+------------+-----------+---------------+-------------+-----------+
```

**해석**

Tx A가 row 1에 S 락과 X 락을 둘 다 보유 중인데, 이는 SERIALIZABLE 격리에서 `findById`(plain SELECT)가 자동으로 S 락을 먼저 잡고 후속 `UPDATE`가 같은 트랜잭션 안에서 그 락을 X로 격상시키면서 남은 자취다. 자기 트랜잭션의 S 락은 자기 X 락 획득을 막지 않으므로 두 락이 함께 기록된다.

한편 Tx B의 plain SELECT는 SERIALIZABLE 격리 안에서 또 다른 S 락 요청이 되는데, Tx A가 보유한 X 락에 막혀 4.556초 동안 대기하다가 Tx A 커밋과 함께 진행한다.

### PESSIMISTIC_WRITE 컬럼

**호출되는 메서드**

```java
// Tx A
PessimisticStockService.updateThenSleep(1L, 1, 5000);  // findByIdForUpdate → decrease → save → sleep 5s
// Tx B
PessimisticStockService.plainSelect(1L);               // findById만 (잠금 없음)
```

**결과**: Tx B 응답 시간 **0.013s**, 값 `100` (커밋 전 snapshot).

**락 상태** — Tx A의 sleep 도중 `data_locks` 출력:

```
+--------+-------------+------------+-----------+---------------+-------------+-----------+
| trx_id | object_name | index_name | lock_type | lock_mode     | lock_status | lock_data |
+--------+-------------+------------+-----------+---------------+-------------+-----------+
|   2390 | stock       | NULL       | TABLE     | IX            | GRANTED     | NULL      |
|   2390 | stock       | PRIMARY    | RECORD    | X,REC_NOT_GAP | GRANTED     | 1         |
+--------+-------------+------------+-----------+---------------+-------------+-----------+
```

**해석**

`findByIdForUpdate`가 처음부터 X 락으로 들어갔기 때문에 S 단계가 없다. Tx B의 `findById`는 잠금 힌트가 없으므로 MVCC consistent read 경로를 탄다 — Tx A의 X 락과 무관하게 즉시 통과하고, Tx A 커밋 전 snapshot인 `100`을 받는다.

**추가 검증** — Tx B가 잠금 경로가 아닌 MVCC 경로를 탔다는 사실은 외부 mysql 세션의 격리 수준을 바꿔가며 직접 판별할 수 있다.

- 외부 세션이 **REPEATABLE READ**로 같은 SELECT → 외부 세션의 record 락이 *전혀 없음* (MVCC 경로)
- 외부 세션이 **SERIALIZABLE**로 같은 SELECT → `RECORD / S,REC_NOT_GAP / WAITING`이 하나 더 추가 (잠금 경로)

따라서 같은 SQL 한 줄이라도 격리 수준 하나에 따라 잠금 경로와 MVCC 경로로 나뉘어 들어간다.

### 동작 차이

- **SERIALIZABLE**: plain SELECT가 자동으로 S 락 요청으로 격상돼 X 락과 충돌
- **PESSIMISTIC_WRITE (REPEATABLE READ)**: plain SELECT가 MVCC 경로라 X 락과 무관하게 통과

---

## 차이 2 — Lost Update가 발생하는 경로

**시나리오**: Tx A가 *읽기만* 한 채 sleep으로 트랜잭션을 잡고 있을 때, Tx B가 같은 row를 UPDATE 한다.

> ⚠️ **이 시나리오의 PESSIMISTIC_WRITE 컬럼은 부정 시연으로 구성했다.** 비관락 전략을 채택했지만 *read 경로*에 `findByIdForUpdate`를 빠뜨린 경우 — 즉 `readThenSleep`이 `findById`를 사용하는 경우 — 어떤 일이 벌어지는지를 보여주기 위해서다. 비관락의 정상 사용법에서는 모든 읽기 경로도 `FOR UPDATE`로 함께 잠가야 한다.

### SERIALIZABLE 컬럼

**호출되는 메서드**

```java
// Tx A
SerializableStockService.readThenSleep(1L, 5000);  // findById → sleep 5s (SERIALIZABLE이므로 S 락 자동)
// Tx B
SerializableStockService.update(1L, 1);            // findById → decrease → save
```

**결과**: Tx B 응답 시간 **4.500s**, 최종 `quantity=99`.

**해석**

Tx A가 plain SELECT만 했는데도 SERIALIZABLE 격리에서 자동으로 S 락이 잡혀 있다. Tx B의 SELECT는 S–S 호환이라 즉시 통과해 값 `100`을 받는다. 그러나 그 다음 `UPDATE`가 X 락을 요청하면서 Tx A의 S 락과 충돌해 4.500초 대기한다.

Tx A 커밋 후에 Tx B가 진행해 row를 `99`로 변경하면서 마무리되므로, Lost Update가 발생할 여지는 없다.

### PESSIMISTIC_WRITE 컬럼

**호출되는 메서드**

```java
// Tx A — 비관락 컬럼이지만 read 경로에 잠금이 없는 부정 시연
PessimisticStockService.readThenSleep(1L, 5000);  // findById만 → sleep 5s  ← FOR UPDATE 빠짐
// Tx B
PessimisticStockService.update(1L, 1);            // findByIdForUpdate → decrease → save
```

**결과**: Tx B 응답 시간 **0.026s**, immediate UPDATE.

**해석**

Tx A는 `findById`로 진입해서 어떤 락도 잡지 않았다. 그 시점의 `data_locks`에 Tx A가 보유한 record 락은 없다 (MVCC 경로).

Tx B의 `findByIdForUpdate`는 X 락을 요청하지만 Tx A가 잡은 락이 없어 즉시 통과하고, UPDATE까지 0.026초 안에 끝낸다. 결과적으로 Tx A는 자기 snapshot `100`을 그대로 본 채 종료되고 Tx B는 row를 `99`로 바꾸는데, 이렇게 read 경로에 잠금이 없을 때 두 트랜잭션이 서로의 변경을 인지하지 못한 채 진행되는 흐름에서 Lost Update가 발생한다.

만약 Tx A가 자기가 본 `100`을 기반으로 quantity를 재계산해 UPDATE 했다면, Tx B의 변경분(`99`)은 그 위로 그대로 덮였을 것이다.

### 동작 차이

**PESSIMISTIC_WRITE 전략**은 잠금 힌트가 붙은 쿼리에만 락을 부여한다. 코드 어딘가에서 read 경로에 `findByIdForUpdate`를 빠뜨리면, 그 사이로 다른 트랜잭션의 UPDATE가 끼어들어 Lost Update가 그대로 발생한다. 보호는 *"모든 읽기/쓰기 경로가 `FOR UPDATE`를 따라야 한다"는 코드 차원의 규약*에 묶여 있다.

**SERIALIZABLE**은 같은 규약을 격리 수준 차원에서 자동으로 강제한다. plain SELECT가 자동으로 S 락이 되므로 read만 하는 트랜잭션도 동시 UPDATE를 차단한다.

---

## 차이 3 — 범위(phantom) 보호

**시나리오**: Tx A가 *범위* SELECT로 여러 row를 본 상태에서, Tx B가 같은 범위 안에 새 row를 INSERT 한다.

### SERIALIZABLE 컬럼

**호출되는 메서드**

```java
// Tx A
SerializableStockService.rangeSelectThenSleep(40, 160, 5000);  // findInQuantityRange + sleep 5s
// Tx B
SerializableStockService.insert(120);                          // 새 Stock(quantity=120) INSERT
```

**결과**: Tx B 응답 시간 **4.576s**, 새 `id=5`.

**락 상태** — Tx A의 sleep 도중 `data_locks` 출력:

```
+-------------+------------+-----------+-----------+-------------+------------------------+
| object_name | index_name | lock_type | lock_mode | lock_status | lock_data              |
+-------------+------------+-----------+-----------+-------------+------------------------+
| stock       | NULL       | TABLE     | IS        | GRANTED     | NULL                   |
| stock       | PRIMARY    | RECORD    | S         | GRANTED     | supremum pseudo-record |
| stock       | PRIMARY    | RECORD    | S         | GRANTED     | 1                      |
| stock       | PRIMARY    | RECORD    | S         | GRANTED     | 2                      |
| stock       | PRIMARY    | RECORD    | S         | GRANTED     | 3                      |
| stock       | PRIMARY    | RECORD    | S         | GRANTED     | 4                      |
+-------------+------------+-----------+-----------+-------------+------------------------+
```

**해석**

`lock_mode`가 `REC_NOT_GAP` 없이 그냥 `S` — gap을 포함한 next-key 락이다. 거기에 인덱스 끝 너머의 가상 record인 `supremum pseudo-record`까지 잡혀 있다.

따라서 "이 범위 안" 또는 "테이블 끝 너머"로의 INSERT가 모두 차단된다. Tx B의 INSERT는 4.576초 대기 후 Tx A 커밋과 함께 진행한다.

### PESSIMISTIC_WRITE 컬럼 — 단일 row 잠금

**호출되는 메서드**

```java
// Tx A
PessimisticStockService.lockSingleRowThenSleep(1L, 5000);  // findByIdForUpdate(1) + sleep 5s
// Tx B
PessimisticStockService.insert(120);                       // 새 Stock(quantity=120) INSERT
```

**결과**: Tx B 응답 시간 **0.044s**, 새 `id=5`.

**해석**

Tx A는 row 1에만 X record 락을 잡았다. `data_locks`에 잡혀 있는 락은 `TABLE / IX` + `RECORD / X,REC_NOT_GAP / lock_data=1` 두 줄뿐이다.

Tx B의 INSERT는 row 1 *밖*에 새 row를 넣는 작업이므로 충돌이 발생하지 않고, 0.044초 안에 INSERT가 끝난다.

### 동작 차이

비관락은 본질적으로 row 단위 도구이기 때문에 범위 보호가 자동으로 따라오지 않는다. 범위 보호가 필요하다면 범위 SELECT 자체에 `FOR UPDATE`를 붙여(`findInQuantityRangeForUpdate(40, 160)`) gap 락을 직접 잡아야 하는데, 결국 SERIALIZABLE이 격리 수준 차원에서 자동으로 수행하던 동작을 코드 차원에서 다시 구현해 보완하는 형태로 이어진다.

---

## 정리

차이 1·2·3의 동작이 두 전략의 어떤 구조적 차이에서 비롯되는지, 결과가 같았던 세 시나리오는 어떤 경계에 놓이는지를 함께 묶어 정리한다.

### 동작 방식

**SERIALIZABLE**은 트랜잭션 격리 수준을 SQL 표준의 최상위 단계로 올린다. 트랜잭션 안의 모든 plain SELECT가 자동으로 S 락 요청으로 격상되고, 범위 SELECT에는 매칭 record와 그 사이 gap에 next-key 락이 함께 잡힌다. 코드 어디에도 잠금 힌트를 적지 않아도 격리 수준 한 줄이 트랜잭션 전체의 잠금 규칙을 강제한다.

**PESSIMISTIC_WRITE**는 격리 수준을 기본(REPEATABLE READ)에 두고, `@Lock(PESSIMISTIC_WRITE)` 힌트가 붙은 쿼리에만 X 락을 부여한다. 잠금 힌트가 없는 SELECT는 MVCC consistent read 경로를 타며 락을 잡지도, 락에 막히지도 않는다.

### 공통점 — 결과가 같은 세 시나리오

다음 세 시나리오에서는 두 전략의 결과가 동일하다.

- **둘 다 UPDATE** — X 락끼리 충돌해 양쪽 모두 4.5초 대기 후 진행, 최종 `quantity=97`
- **한쪽이 명시적 `SELECT FOR UPDATE`** — 양쪽 모두 4.5초 대기 후 `99`
- **둘 다 plain SELECT** — 양쪽 모두 즉시 응답, `100`

세 시나리오는 모두 X 락끼리의 충돌만 일어나거나, 아예 충돌이 발생하지 않는 경계에 놓여 있어서 두 전략이 같은 결정을 내릴 수밖에 없다. "두 전략이 똑같다"는 인상은 보통 이 셋만 머릿속에 그릴 때 생기고, 그 외 차이 1·2·3에서는 동작이 갈라진다.

### 차이점 — 구조와 동작

| 항목 | SERIALIZABLE | PESSIMISTIC_WRITE |
|---|---|---|
| 적용 방식 | 트랜잭션 격리 수준 선언 (자동) | `@Lock` 힌트 (명시) |
| plain SELECT 차단 | O (자동 S 락 격상) | X (MVCC snapshot) |
| 범위(phantom) 보호 | O (자동 gap lock) | 범위 쿼리에 `FOR UPDATE`를 직접 붙여야 함 |
| Lost Update 차단 | 격리 수준 차원에서 자동 | 모든 읽기 경로가 `FOR UPDATE`를 따라야 성립 |
| reader 처리량 | reader까지 막아 낮음 | reader 통과로 상대적으로 높음 |

구조적으로 보면 두 전략은 추상화 수준이 다르다. SERIALIZABLE은 *격리 수준*이라 한 줄 선언으로 트랜잭션 안의 모든 동작에 자동으로 적용되는 반면, PESSIMISTIC_WRITE는 *도구*라 잠금 힌트가 붙은 쿼리에만 작동한다. 이 추상화 차이가 위 표 다섯 행의 동작 차이를 만든다.

특히 Lost Update 차단은 두 전략의 책임 위치가 다르다. SERIALIZABLE은 격리 수준 차원에서 자동으로 강제되는 반면, PESSIMISTIC_WRITE는 "모든 읽기 경로에 `FOR UPDATE`를 빠뜨리지 않는다"는 규약을 코드 차원에서 지켜야 성립하고, 차이 2의 부정 시연이 그 규약을 어겼을 때 어떤 결과로 이어지는지를 그대로 보여준다.

### 어떤 상황에 어울리는가

트레이드오프는 다음과 같이 정렬된다.

- **SERIALIZABLE** — reader까지 자동으로 잠가 안전 범위가 넓고, 그만큼 처리량 손해가 크다.
- **PESSIMISTIC_WRITE** — reader를 통과시켜 처리량은 낫지만, `read → 계산 → write` 패턴의 *모든* read 단계에 `FOR UPDATE`를 빠뜨리지 않는다는 규약을 코드 차원에서 지켜야 한다.

도메인별로는 다음과 같이 갈린다.

- 재고/좋아요/쿠폰처럼 `read → 검증 → write` 패턴이 분명하고 잠금 지점이 한정된 도메인 → **PESSIMISTIC_WRITE**가 자연스럽다.
- 읽기 경로가 코드 곳곳에 흩어져 `FOR UPDATE` 규약을 강제하기 어려운 환경 → **SERIALIZABLE**이 더 안전하다.

---

> 실험 코드: `src/main/java/lab/stock/*`
> 측정 자동화/원본 로그: `.claude/plan.md`, `.claude/results.md`
