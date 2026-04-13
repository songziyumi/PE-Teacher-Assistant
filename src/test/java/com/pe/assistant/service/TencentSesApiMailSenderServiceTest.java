package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.MailOutbox;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TencentSesApiMailSenderServiceTest {

    @Mock
    private AppMailProperties appMailProperties;
    @Mock
    private AppMailProperties.SesApi sesApi;
    @Mock
    private TencentSesClientFactory tencentSesClientFactory;
    @Mock
    private SesClient sesClient;

    @InjectMocks
    private TencentSesApiMailSenderService tencentSesApiMailSenderService;

    @Test
    void sendShouldBuildTemplateRequest() throws Exception {
        MailOutbox outbox = new MailOutbox();
        outbox.setRecipientEmail("student@example.com");
        outbox.setSubject("邮箱验证");
        outbox.setTemplateId(201L);
        outbox.setTemplateData("{\"verifyToken\":\"raw-token\"}");

        SendEmailResponse response = new SendEmailResponse();
        response.setMessageId("msg-1");
        response.setRequestId("req-1");

        when(appMailProperties.getFrom()).thenReturn("no-reply@example.com");
        when(appMailProperties.getSesApi()).thenReturn(sesApi);
        when(sesApi.requireSecretId()).thenReturn("sid");
        when(sesApi.requireSecretKey()).thenReturn("skey");
        when(sesApi.resolveRegion()).thenReturn("ap-guangzhou");
        when(sesApi.resolveEndpoint()).thenReturn("ses.tencentcloudapi.com");
        when(sesApi.resolveTriggerType()).thenReturn(1L);
        when(tencentSesClientFactory.create(any(), eq("ap-guangzhou"), any())).thenReturn(sesClient);
        when(sesClient.SendEmail(any(SendEmailRequest.class))).thenReturn(response);

        MailSendResult result = tencentSesApiMailSenderService.send(outbox);

        assertEquals("msg-1", result.providerMessageId());
        assertEquals("req-1", result.providerRequestId());
        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesClient).SendEmail(requestCaptor.capture());
        assertEquals("邮箱验证", requestCaptor.getValue().getSubject());
    }

    @Test
    void sendShouldFailWhenTemplateIdMissing() {
        MailOutbox outbox = new MailOutbox();
        outbox.setRecipientEmail("student@example.com");
        outbox.setSubject("密码重置");

        assertThrows(IllegalArgumentException.class, () -> tencentSesApiMailSenderService.send(outbox));
    }
}
