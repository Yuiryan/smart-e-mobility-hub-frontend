package vn.edu.bku.smart_emobility_backend.common.mail;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import vn.edu.bku.smart_emobility_backend.common.exception.RecoveryDeliveryException;

@Component
public class SmtpRecoveryMailSender implements RecoveryMailSender {

    private final JavaMailSender sender;
    private final String from;

    public SmtpRecoveryMailSender(
            JavaMailSender sender,
            @Value("${app.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void sendRecoveryCode(String email, String code, Instant expiresAt) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Smart E-Mobility Hub - Password recovery");
        message.setText(
                "Your password recovery code is: " + code
                + "\nValid until: " + expiresAt
                + "\nRequest a new code if this code has expired.");

        try {
            sender.send(message);
        } catch (MailException exception) {
            throw new RecoveryDeliveryException(exception);
        }
    }
}
