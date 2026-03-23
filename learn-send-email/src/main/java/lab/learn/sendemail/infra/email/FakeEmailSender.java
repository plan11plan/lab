package lab.learn.sendemail.infra.email;

import lab.learn.sendemail.domain.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("fake")
public class FakeEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("========== [FAKE] 이메일 발송 시뮬레이션 ==========");
        log.info("[FAKE] To      : {}", to);
        log.info("[FAKE] Subject : {}", subject);
        log.info("[FAKE] Body    : {}", body);
        log.info("========== [FAKE] 발송 완료 (실제 전송 없음) ==========");
    }
}
