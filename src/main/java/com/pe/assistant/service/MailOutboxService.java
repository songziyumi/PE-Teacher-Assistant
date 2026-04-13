package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.AccountPrincipalType;
import com.pe.assistant.entity.MailOutbox;
import com.pe.assistant.entity.MailOutboxStatus;
import com.pe.assistant.repository.MailOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MailOutboxService {

    private final MailOutboxRepository mailOutboxRepository;
    private final AppMailProperties appMailProperties;

    @Transactional
    public MailOutbox queue(String bizType,
                            AccountPrincipalType principalType,
                            Long principalId,
                            String recipientEmail,
                            String subject,
                            String bodyText,
                            String bodyHtml) {
        MailOutbox outbox = new MailOutbox();
        outbox.setBizType(bizType);
        outbox.setPrincipalType(principalType);
        outbox.setPrincipalId(principalId);
        outbox.setRecipientEmail(recipientEmail);
        outbox.setSubject(subject);
        outbox.setTemplateId(null);
        outbox.setTemplateData(null);
        outbox.setBodyText(bodyText);
        outbox.setBodyHtml(bodyHtml);
        outbox.setStatus(MailOutboxStatus.PENDING);
        outbox.setNextRetryAt(null);
        return mailOutboxRepository.save(outbox);
    }

    @Transactional
    public MailOutbox queueTemplate(String bizType,
                                    AccountPrincipalType principalType,
                                    Long principalId,
                                    String recipientEmail,
                                    String subject,
                                    Long templateId,
                                    String templateData,
                                    String bodyText,
                                    String bodyHtml) {
        MailOutbox outbox = new MailOutbox();
        outbox.setBizType(bizType);
        outbox.setPrincipalType(principalType);
        outbox.setPrincipalId(principalId);
        outbox.setRecipientEmail(recipientEmail);
        outbox.setSubject(subject);
        outbox.setTemplateId(templateId);
        outbox.setTemplateData(templateData);
        outbox.setBodyText(bodyText);
        outbox.setBodyHtml(bodyHtml);
        outbox.setStatus(MailOutboxStatus.PENDING);
        outbox.setNextRetryAt(null);
        return mailOutboxRepository.save(outbox);
    }

    @Transactional(readOnly = true)
    public List<MailOutbox> findPendingDispatchBatch() {
        return mailOutboxRepository.findDispatchBatch(
                MailOutboxStatus.PENDING,
                LocalDateTime.now(),
                PageRequest.of(0, appMailProperties.resolveDispatchBatchSize()));
    }

    @Transactional
    public MailOutbox markSent(MailOutbox outbox, MailSendResult mailSendResult) {
        outbox.setStatus(MailOutboxStatus.SENT);
        outbox.setSentAt(LocalDateTime.now());
        outbox.setLastError(null);
        outbox.setNextRetryAt(null);
        outbox.setProviderMessageId(mailSendResult != null ? truncate(mailSendResult.providerMessageId(), 120) : null);
        outbox.setProviderRequestId(mailSendResult != null ? truncate(mailSendResult.providerRequestId(), 120) : null);
        return mailOutboxRepository.save(outbox);
    }

    @Transactional
    public MailOutbox markRetry(MailOutbox outbox, Exception exception) {
        int retryCount = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
        outbox.setRetryCount(retryCount);
        outbox.setLastError(truncate(resolveErrorMessage(exception), 500));
        outbox.setSentAt(null);
        if (retryCount >= appMailProperties.resolveMaxRetryCount()) {
            outbox.setStatus(MailOutboxStatus.FAILED);
            outbox.setNextRetryAt(null);
        } else {
            outbox.setStatus(MailOutboxStatus.PENDING);
            outbox.setNextRetryAt(LocalDateTime.now().plusMinutes(appMailProperties.resolveRetryDelayMinutes()));
        }
        return mailOutboxRepository.save(outbox);
    }

    private String resolveErrorMessage(Exception exception) {
        if (exception == null) {
            return "unknown mail dispatch error";
        }
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return exception.getClass().getSimpleName() + ": " + message;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
