package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.MailOutbox;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailSenderService {

    private final AppMailProperties appMailProperties;
    private final SmtpMailSenderService smtpMailSenderService;
    private final TencentSesApiMailSenderService tencentSesApiMailSenderService;

    public MailSendResult send(MailOutbox outbox) throws Exception {
        if (appMailProperties.isSesApiTransport()) {
            return tencentSesApiMailSenderService.send(outbox);
        }
        if (appMailProperties.isSmtpTransport()) {
            return smtpMailSenderService.send(outbox);
        }
        throw new IllegalStateException("不支持的邮件传输方式: " + appMailProperties.getTransport());
    }
}
