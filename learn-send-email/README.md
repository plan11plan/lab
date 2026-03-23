# learn-send-email

Spring Boot + Gmail SMTP를 이용한 이메일 발송 학습 모듈.

`EmailSender` 인터페이스를 정의하고, `GoogleEmailSender`(실제 발송)와 `FakeEmailSender`(로그만 출력)를 프로필로 전환하는 구조이다.

---

## 환경 정보

| 항목 | 버전 |
|------|------|
| Java | 21 |
| Spring Boot | 4.0.3 |
| spring-boot-starter-mail | Spring Boot BOM 기준 |
| Build Tool | Gradle (멀티모듈) |

---

## 프로젝트 구조

```
learn-send-email/
├── build.gradle
├── api.http                                  # HTTP 요청 테스트
└── src/main/java/lab/learn/sendemail/
    ├── Application.java
    │
    ├── interfaces/                           ← 프레젠테이션 계층
    │   ├── EmailController.java              POST /email/send
    │   └── EmailSendRequest.java             요청 DTO (record)
    │
    ├── facade/                               ← 애플리케이션 계층
    │   └── EmailFacade.java                  외부 기능 조합
    │
    ├── domain/                               ← 도메인 계층
    │   └── EmailSender.java                  이메일 발송 인터페이스 (포트)
    │
    └── infra/email/                          ← 인프라 계층 (구현체)
        ├── GoogleEmailSender.java            @Profile("!fake") — Gmail SMTP
        └── FakeEmailSender.java              @Profile("fake")  — 로그만 출력
```

### 의존 방향

```
interfaces → facade → domain ← infra
```

- `domain`은 어디에도 의존하지 않는다. `EmailSender` 인터페이스만 정의한다.
- `infra`는 `domain`의 인터페이스를 구현한다. (의존성 역전)
- `facade`는 `domain`의 인터페이스에만 의존하고, 어떤 구현체가 주입되는지 모른다.
- `interfaces`는 `facade`만 호출한다.

---

## 사용법

### 1. Fake 모드 (SMTP 설정 없이 테스트)

```bash
./gradlew :learn-send-email:bootRun --args='--spring.profiles.active=fake'
```

실제 메일을 보내지 않고 로그만 출력한다. SMTP 크레덴셜이 필요 없다.

```
========== [FAKE] 이메일 발송 시뮬레이션 ==========
[FAKE] To      : receiver@example.com
[FAKE] Subject : 테스트 이메일
[FAKE] Body    : Spring Boot에서 발송한 테스트 이메일입니다.
========== [FAKE] 발송 완료 (실제 전송 없음) ==========
```

### 2. Google 모드 (실제 Gmail 발송)

#### Gmail 앱 비밀번호 발급

1. [Google 계정](https://myaccount.google.com/) → 보안 → 2단계 인증 활성화
2. 2단계 인증 → 앱 비밀번호 → 앱 이름 입력 → 생성
3. 16자리 비밀번호를 복사한다

#### 실행

```bash
GMAIL_USERNAME=your@gmail.com GMAIL_APP_PASSWORD=xxxx-xxxx-xxxx-xxxx \
  ./gradlew :learn-send-email:bootRun
```

### 3. API 호출

```http
POST http://localhost:8080/email/send
Content-Type: application/json

{
  "to": "receiver@example.com",
  "subject": "테스트 이메일",
  "body": "Spring Boot에서 발송한 테스트 이메일입니다."
}
```

성공 시 응답: `200 OK` — `이메일 발송 완료`

---

## 핵심 코드

### EmailSender (domain)

```java
public interface EmailSender {
    void send(String to, String subject, String body);
}
```

`domain` 계층에 위치한 포트 인터페이스. `facade`는 이 인터페이스에만 의존하므로, 구현체가 Google이든 Fake이든 상관없이 동작한다.

### GoogleEmailSender (infra)

```java
@Component
@Profile("!fake")
public class GoogleEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
    }
}
```

`spring-boot-starter-mail`이 자동 구성하는 `JavaMailSender`를 주입받아 `SimpleMailMessage`로 발송한다. `application.yaml`의 SMTP 설정을 그대로 사용한다.

### FakeEmailSender (infra)

```java
@Component
@Profile("fake")
public class FakeEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("[FAKE] To: {}, Subject: {}, Body: {}", to, subject, body);
    }
}
```

`@Profile("fake")`이 활성화되면 `GoogleEmailSender` 대신 이 빈이 등록된다. `application-fake.yaml`에서 `MailSenderAutoConfiguration`을 제외하므로 SMTP 연결을 시도하지 않는다.

### 프로필 전환 원리

| 프로필 | EmailSender 구현체 | SMTP 연결 | 용도 |
|--------|---------------------|-----------|------|
| (기본) | `GoogleEmailSender` | O | 실제 Gmail 발송 |
| `fake` | `FakeEmailSender` | X | 로그로 요청/응답 확인 |

`@Profile("!fake")`는 "fake 프로필이 아닐 때 활성화"를 의미한다. 기본 프로필에서는 `GoogleEmailSender`가, `fake` 프로필에서는 `FakeEmailSender`가 빈으로 등록된다.
