package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.MailOutbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailSenderServiceTest {

    @Mock
    private AppMailProperties appMailProperties;
    @Mock
    private SmtpMailSenderService smtpMailSenderService;
    @Mock
    private TencentSesApiMailSenderService tencentSesApiMailSenderService;

    @InjectMocks
    private MailSenderService mailSenderService;

    @Test
    void sendShouldDelegateToSesApiTransport() throws Exception {
        MailOutbox outbox = new MailOutbox();
        when(appMailProperties.isSesApiTransport()).thenReturn(true);
        when(tencentSesApiMailSenderService.send(outbox)).thenReturn(new MailSendResult("msg-1", "req-1"));

        MailSendResult result = mailSenderService.send(outbox);

        assertEquals("msg-1", result.providerMessageId());
        verify(tencentSesApiMailSenderService).send(outbox);
    }

    @Test
    void sendShouldDelegateToSmtpTransport() throws Exception {
        MailOutbox outbox = new MailOutbox();
        when(appMailProperties.isSesApiTransport()).thenReturn(false);
        when(appMailProperties.isSmtpTransport()).thenReturn(true);
        when(smtpMailSenderService.send(outbox)).thenReturn(new MailSendResult(null, null));

        mailSenderService.send(outbox);

        verify(smtpMailSenderService).send(outbox);
    }
}
