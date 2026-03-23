package lab.learn.sendemail.interfaces;

public record EmailSendRequest(
        String to,
        String subject,
        String body
) {
}
