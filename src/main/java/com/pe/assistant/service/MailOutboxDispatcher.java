package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.MailOutbox;
import com.pe.assistant.entity.MailOutboxStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailOutboxDispatcher {

    private final AppMailProperties appMailProperties;
    private final MailOutboxService mailOutboxService;
    private final MailSenderService mailSenderService;

    @Scheduled(fixedDelayString = "${app.mail.dispatch-fixed-delay-ms:30000}")
    public void dispatchPendingMails() {
        if (!appMailProperties.isEnabled() || !appMailProperties.isDispatchEnabled()) {
            return;
        }

        List<MailOutbox> pendingMails = mailOutboxService.findPendingDispatchBatch();
        for (MailOutbox outbox : pendingMails) {
            try {
                MailSendResult mailSendResult = mailSenderService.send(outbox);
                mailOutboxService.markSent(outbox, mailSendResult);
            } catch (Exception exception) {
                MailOutbox updated = mailOutboxService.markRetry(outbox, exception);
                if (updated.getStatus() == MailOutboxStatus.FAILED) {
                    log.error("邮件发送失败且不再重试，outboxId={} recipient={}",
                            outbox.getId(), outbox.getRecipientEmail(), exception);
                } else {
                    log.warn("邮件发送失败，将稍后重试，outboxId={} recipient={} retryCount={}",
                            outbox.getId(), outbox.getRecipientEmail(), updated.getRetryCount(), exception);
                }
            }
        }
    }
}
