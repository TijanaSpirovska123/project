package com.example.marketing.email.api;

import com.example.marketing.email.service.EmailService;
import com.example.marketing.email.entity.EmailRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping()
    public String sendMail(@RequestBody EmailRequest details) {
        return emailService.sendMail(details);
    }

}
