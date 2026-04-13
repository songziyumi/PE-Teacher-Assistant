package com.pe.assistant.service;

import com.pe.assistant.config.AppMailProperties;
import com.pe.assistant.entity.MailOutbox;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.SendEmailResponse;
import com.tencentcloudapi.ses.v20201002.models.Template;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TencentSesApiMailSenderService {

    private final AppMailProperties appMailProperties;
    private final TencentSesClientFactory tencentSesClientFactory;

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
        if (outbox.getTemplateId() == null || outbox.getTemplateId() <= 0) {
            throw new IllegalArgumentException("SES API 发信必须提供模板 ID");
        }
        if (isBlank(outbox.getTemplateData())) {
            throw new IllegalArgumentException("SES API 发信必须提供模板数据");
        }

        AppMailProperties.SesApi sesApi = appMailProperties.getSesApi();
        Credential credential = new Credential(sesApi.requireSecretId(), sesApi.requireSecretKey());
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint(sesApi.resolveEndpoint());

        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);

        SesClient sesClient = tencentSesClientFactory.create(credential, sesApi.resolveRegion(), clientProfile);

        SendEmailRequest request = new SendEmailRequest();
        request.setFromEmailAddress(appMailProperties.getFrom());
        request.setDestination(new String[]{outbox.getRecipientEmail().trim()});
        request.setSubject(outbox.getSubject().trim());
        request.setTriggerType(sesApi.resolveTriggerType());
        if (!isBlank(sesApi.getReplyToAddress())) {
            request.setReplyToAddresses(sesApi.getReplyToAddress().trim());
        }

        Template template = new Template();
        template.setTemplateID(outbox.getTemplateId());
        template.setTemplateData(outbox.getTemplateData());
        request.setTemplate(template);

        SendEmailResponse response = sesClient.SendEmail(request);
        String messageId = response.getMessageId();
        String requestId = response.getRequestId();
        return new MailSendResult(messageId, requestId);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }
}
