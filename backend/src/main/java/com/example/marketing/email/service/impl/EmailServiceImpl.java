package com.example.marketing.email.service.impl;

import com.example.marketing.email.entity.EmailRequest;
import com.example.marketing.email.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String sender;

    public String sendMail(EmailRequest details) {

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();

            mailMessage.setFrom(sender);
            mailMessage.setTo(details.getRecipient());
            mailMessage.setText(details.getMessageBody());
            mailMessage.setSubject(details.getSubject());


            javaMailSender.send(mailMessage);
            return "Mail Sent Successfully...";
        }

        catch (Exception e) {
            return "Error while Sending Mail";
        }
    }
    @Override
    public void sendPasswordResetEmail(String email, String token) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();

            mailMessage.setFrom(sender);
            mailMessage.setTo(email);
            mailMessage.setSubject("Password Reset Request");
            mailMessage.setText("To reset your password, please use the following token: " + token);

            javaMailSender.send(mailMessage);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error while sending password reset email", e);
        }
    }
}