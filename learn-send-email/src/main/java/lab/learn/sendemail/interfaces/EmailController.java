package lab.learn.sendemail.interfaces;

import lab.learn.sendemail.facade.EmailFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailFacade emailFacade;

    @PostMapping("/send")
    public ResponseEntity<String> send(@RequestBody EmailSendRequest request) {
        log.info("이메일 발송 요청 - to: {}, subject: {}", request.to(), request.subject());
        emailFacade.sendEmail(request.to(), request.subject(), request.body());
        return ResponseEntity.ok("이메일 발송 완료");
    }
}
