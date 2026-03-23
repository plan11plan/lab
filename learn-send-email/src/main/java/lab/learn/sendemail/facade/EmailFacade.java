package lab.learn.sendemail.facade;

import lab.learn.sendemail.domain.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailFacade {

    private final EmailSender emailSender;

    public void sendEmail(String to, String subject, String body) {
        log.info("EmailFacade.sendEmail() - to: {}", to);
        emailSender.send(to, subject, body);
    }
}
