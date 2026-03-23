package lab.learn.sendemail.infra.email;

import lab.learn.sendemail.domain.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!fake")
@RequiredArgsConstructor
public class GoogleEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;

    @Override
    public void send(String to, String subject, String body) {
        log.info("Google 이메일 발송 시작 - to: {}, subject: {}", to, subject);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        javaMailSender.send(message);

        log.info("Google 이메일 발송 완료 - to: {}", to);
    }
}
