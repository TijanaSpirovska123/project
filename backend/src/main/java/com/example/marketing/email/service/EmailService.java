package com.example.marketing.email.service;

import com.example.marketing.email.entity.EmailRequest;


public interface EmailService {

    String sendMail(EmailRequest emailRequest);

    void sendPasswordResetEmail(String email, String token);
}
