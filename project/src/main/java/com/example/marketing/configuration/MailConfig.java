package com.example.marketing.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${spring.mail.host:smtp.mail.com}")
    private String host;

    @Value("${spring.mail.port:587}")
    private int port;

    @Value("${spring.mail.username:user@mail.com}")
    private String username;

    @Value("${spring.mail.password:password}")
    private String password;

    // Properties

    @Value("${spring.mail.properties.mail.transport.protocol:smtp}")
    private String propertiesProtocol;

    @Value("${spring.mail.properties.mail.smtp.auth:true}")
    private String propertiesAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
    private String propertiesStarttls;

    @Value("${spring.mail.properties.mail.smtp.ssl.trust:smtp.mail.com}")
    private String propertiesSslTrust;

    @Value("${spring.mail.properties.debug:false}")
    private String propertiesDebug;


    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", propertiesProtocol);
        props.put("mail.smtp.auth", propertiesAuth);
        props.put("mail.smtp.starttls.enable", propertiesStarttls);
        props.put("mail.smtp.ssl.trust", propertiesSslTrust);
        props.put("mail.debug", propertiesDebug);

        return mailSender;
    }
}
