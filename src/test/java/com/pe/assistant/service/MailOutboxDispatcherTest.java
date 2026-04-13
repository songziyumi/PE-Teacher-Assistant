package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.MailOutbox;
import com.pe.assistant.entity.MailOutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxDispatcherTest {

    @Mock
    private AppMailProperties appMailProperties;
    @Mock
    private MailOutboxService mailOutboxService;
    @Mock
    private MailSenderService mailSenderService;

    @InjectMocks
    private MailOutboxDispatcher mailOutboxDispatcher;

    @Test
    void dispatchPendingMailsShouldSendAndMarkSent() throws Exception {
        MailOutbox outbox = new MailOutbox();
        outbox.setId(1L);

        when(appMailProperties.isEnabled()).thenReturn(true);
        when(appMailProperties.isDispatchEnabled()).thenReturn(true);
        when(mailOutboxService.findPendingDispatchBatch()).thenReturn(List.of(outbox));
        when(mailSenderService.send(outbox)).thenReturn(new MailSendResult(null, "req-1"));

        mailOutboxDispatcher.dispatchPendingMails();

        verify(mailSenderService).send(outbox);
        verify(mailOutboxService).markSent(eq(outbox), any(MailSendResult.class));
    }

    @Test
    void dispatchPendingMailsShouldMarkRetryWhenSendFails() throws Exception {
        MailOutbox outbox = new MailOutbox();
        outbox.setId(2L);
        outbox.setRecipientEmail("student@example.com");
        MailOutbox failed = new MailOutbox();
        failed.setStatus(MailOutboxStatus.FAILED);
        failed.setRetryCount(3);

        when(appMailProperties.isEnabled()).thenReturn(true);
        when(appMailProperties.isDispatchEnabled()).thenReturn(true);
        when(mailOutboxService.findPendingDispatchBatch()).thenReturn(List.of(outbox));
        when(mailOutboxService.markRetry(any(MailOutbox.class), any(Exception.class))).thenReturn(failed);
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp down")).when(mailSenderService).send(outbox);

        mailOutboxDispatcher.dispatchPendingMails();

        verify(mailOutboxService).markRetry(any(MailOutbox.class), any(Exception.class));
        verify(mailOutboxService, never()).markSent(any(MailOutbox.class), any(MailSendResult.class));
    }

    @Test
    void dispatchPendingMailsShouldSkipWhenMailDisabled() {
        when(appMailProperties.isEnabled()).thenReturn(false);

        mailOutboxDispatcher.dispatchPendingMails();

        verify(mailOutboxService, never()).findPendingDispatchBatch();
    }
}
