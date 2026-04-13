package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.MailOutbox;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class SmtpMailSenderService {

    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;
    private final AppMailProperties appMailProperties;

    public MailSendResult send(MailOutbox outbox) throws Exception {
        if (outbox == null) {
            throw new IllegalArgumentException("邮件任务不能为空");
        }
        if (isBlank(outbox.getRecipientEmail())) {
            throw new IllegalArgumentException("收件邮箱不能为空");
        }
        if (isBlank(outbox.getSubject())) {
            throw new IllegalArgumentException("邮件主题不能为空");
        }

        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            throw new IllegalStateException("未配置 SMTP 发信器，请检查 spring.mail.* 或腾讯云 SES SMTP 配置");
        }

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
        helper.setFrom(appMailProperties.getFrom());
        helper.setTo(outbox.getRecipientEmail().trim());
        helper.setSubject(outbox.getSubject().trim());

        String bodyText = normalize(outbox.getBodyText());
        String bodyHtml = normalize(outbox.getBodyHtml());
        if (bodyHtml != null) {
            helper.setText(bodyText != null ? bodyText : stripHtml(bodyHtml), bodyHtml);
        } else {
            helper.setText(bodyText != null ? bodyText : "");
        }

        javaMailSender.send(mimeMessage);
        return new MailSendResult(null, null);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }
}
