package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.MailOutbox;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpMailSenderServiceTest {

    @Mock
    private ObjectProvider<JavaMailSender> javaMailSenderProvider;
    @Mock
    private AppMailProperties appMailProperties;
    @Mock
    private JavaMailSender javaMailSender;

    @InjectMocks
    private SmtpMailSenderService smtpMailSenderService;

    @Test
    void sendShouldBuildMimeMessage() throws Exception {
        MailOutbox outbox = new MailOutbox();
        outbox.setRecipientEmail("student@example.com");
        outbox.setSubject("测试邮件");
        outbox.setBodyText("纯文本内容");
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

        when(javaMailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(appMailProperties.getFrom()).thenReturn("no-reply@example.com");

        smtpMailSenderService.send(outbox);

        Address[] recipients = mimeMessage.getAllRecipients();
        assertEquals("student@example.com", recipients[0].toString());
        assertEquals("测试邮件", mimeMessage.getSubject());
        verify(javaMailSender).send(mimeMessage);
    }

    @Test
    void sendShouldFailWhenSenderNotConfigured() {
        MailOutbox outbox = new MailOutbox();
        outbox.setRecipientEmail("student@example.com");
        outbox.setSubject("测试邮件");

        when(javaMailSenderProvider.getIfAvailable()).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> smtpMailSenderService.send(outbox));
    }
}
