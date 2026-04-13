package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.MailOutbox;
import com.pe.assistant.entity.MailOutboxStatus;
import com.pe.assistant.repository.MailOutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOutboxServiceTest {

    @Mock
    private MailOutboxRepository mailOutboxRepository;

    @Mock
    private AppMailProperties appMailProperties;

    @InjectMocks
    private MailOutboxService mailOutboxService;

    @Test
    void markRetryShouldKeepPendingWhenBelowRetryLimit() {
        MailOutbox outbox = new MailOutbox();
        outbox.setRetryCount(0);
        outbox.setStatus(MailOutboxStatus.PENDING);

        when(appMailProperties.resolveMaxRetryCount()).thenReturn(3);
        when(appMailProperties.resolveRetryDelayMinutes()).thenReturn(5);
        when(mailOutboxRepository.save(any(MailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MailOutbox saved = mailOutboxService.markRetry(outbox, new IllegalStateException("smtp timeout"));

        assertEquals(MailOutboxStatus.PENDING, saved.getStatus());
        assertEquals(1, saved.getRetryCount());
        assertNotNull(saved.getNextRetryAt());
        assertTrue(saved.getLastError().contains("smtp timeout"));
    }

    @Test
    void markRetryShouldFailWhenReachRetryLimit() {
        MailOutbox outbox = new MailOutbox();
        outbox.setRetryCount(2);
        outbox.setStatus(MailOutboxStatus.PENDING);

        when(appMailProperties.resolveMaxRetryCount()).thenReturn(3);
        when(mailOutboxRepository.save(any(MailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MailOutbox saved = mailOutboxService.markRetry(outbox, new IllegalStateException("smtp auth failed"));

        assertEquals(MailOutboxStatus.FAILED, saved.getStatus());
        assertEquals(3, saved.getRetryCount());
        assertNull(saved.getNextRetryAt());
    }

    @Test
    void findPendingDispatchBatchShouldUseConfiguredBatchSize() {
        when(appMailProperties.resolveDispatchBatchSize()).thenReturn(10);
        when(mailOutboxRepository.findDispatchBatch(any(), any(), any())).thenReturn(List.of(new MailOutbox()));

        List<MailOutbox> result = mailOutboxService.findPendingDispatchBatch();

        assertEquals(1, result.size());
        ArgumentCaptor<org.springframework.data.domain.Pageable> pageableCaptor =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(mailOutboxRepository).findDispatchBatch(any(), any(), pageableCaptor.capture());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void markSentShouldPersistProviderIds() {
        MailOutbox outbox = new MailOutbox();
        outbox.setStatus(MailOutboxStatus.PENDING);
        when(mailOutboxRepository.save(any(MailOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MailOutbox saved = mailOutboxService.markSent(outbox, new MailSendResult("msg-1", "req-1"));

        assertEquals(MailOutboxStatus.SENT, saved.getStatus());
        assertEquals("msg-1", saved.getProviderMessageId());
        assertEquals("req-1", saved.getProviderRequestId());
        assertNotNull(saved.getSentAt());
    }
}
