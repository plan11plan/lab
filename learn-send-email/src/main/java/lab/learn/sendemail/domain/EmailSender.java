package lab.learn.sendemail.domain;

public interface EmailSender {

    void send(String to, String subject, String body);
}
