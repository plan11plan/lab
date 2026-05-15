# lab

호기심을 실험·실측으로 검증하는 저장소입니다.

## 운영 방식

- `main` 브랜치는 **목차**입니다. README와 정리된 문서(`docs/`)만 둡니다. 실험 코드는 올라가지 않습니다.
- 주제 하나 = 브랜치 하나. 모든 실험 코드는 해당 브랜치 안에서 살아 있습니다.
- 실험이 끝나면 정리된 글만 `docs/`로 가져와 `main`에 합치고, 이 README에 한 행을 추가합니다.
- 이렇게 두면 새 주제를 시작할 때 항상 깨끗한 `main`에서 브랜치를 분기할 수 있습니다.

## 주제 목록

### Database

| 주제 | 브랜치 | 문서 |
|---|---|---|
| Lost Update를 막는 두 방법 — SERIALIZABLE vs PESSIMISTIC_WRITE 실측 | [`pessimistic-lock-vs-serializable`](https://github.com/plan11plan/lab/tree/pessimistic-lock-vs-serializable) | [SERIALIZABLE vs 비관락](docs/SERIALIZABLE%20vs%20%EB%B9%84%EA%B4%80%EB%9D%BD.md) |
| 동시성 테스트로 race condition 재현하기 — 단일 Latch / 3-Latch / CompletableFuture | [`concurrency-how-to-testing`](https://github.com/plan11plan/lab/tree/concurrency-how-to-testing) | [동시성 테스트 도구](docs/%EB%8F%99%EC%8B%9C%EC%84%B1%ED%85%8C%EC%8A%A4%ED%8A%B8%20%EB%8F%84%EA%B5%AC.md) |

<!-- 새 주제를 추가할 때 위 표에 한 행씩 늘립니다. 카테고리(### Database 같은 h3)는 필요에 따라 추가합니다. -->

## 새 실험 시작하기

```bash
# 1. main 에서 분기
git checkout main
git pull
git checkout -b <topic-branch-name>

# 2. 실험 코드 작성·측정 (이 브랜치 안에서만 산다)

# 3. 글이 정리되면 docs/<주제>.md 로 저장하고 README 표에 한 행 추가
#    → main 으로 가져갈 때는 docs/ + README 변경분만 골라서 반영
```
