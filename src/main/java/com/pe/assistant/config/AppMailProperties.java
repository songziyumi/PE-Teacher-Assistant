package com.pe.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.mail")
public class AppMailProperties {

    private boolean enabled = false;
    private String transport = "smtp";
    private String productName = "体育教师助手";
    private String from = "no-reply@example.com";
    private String baseUrl = "http://localhost:8080";
    private boolean dispatchEnabled = true;
    private int dispatchBatchSize = 20;
    private long dispatchFixedDelayMs = 30000L;
    private int maxRetryCount = 3;
    private int retryDelayMinutes = 5;
    private int verifyEmailExpireMinutes = 30;
    private int resetPasswordExpireMinutes = 30;
    private int verifyEmailLimitPerAccount = 3;
    private int verifyEmailLimitPerIp = 10;
    private int resetPasswordLimitPerAccount = 3;
    private int resetPasswordLimitPerIp = 10;
    private SesApi sesApi = new SesApi();

    public boolean isSmtpTransport() {
        return "smtp".equalsIgnoreCase(transport);
    }

    public boolean isSesApiTransport() {
        return "ses-api".equalsIgnoreCase(transport) || "ses_api".equalsIgnoreCase(transport);
    }

    public int resolveDispatchBatchSize() {
        return Math.max(dispatchBatchSize, 1);
    }

    public int resolveMaxRetryCount() {
        return Math.max(maxRetryCount, 1);
    }

    public int resolveRetryDelayMinutes() {
        return Math.max(retryDelayMinutes, 1);
    }

    @Data
    public static class SesApi {
        private String secretId;
        private String secretKey;
        private String region = "ap-guangzhou";
        private String endpoint = "ses.tencentcloudapi.com";
        private String replyToAddress;
        private Long triggerType = 1L;
        private Long verifyEmailTemplateId;
        private Long resetPasswordTemplateId;

        public String requireSecretId() {
            if (secretId == null || secretId.isBlank()) {
                throw new IllegalStateException("未配置 APP_MAIL_SES_API_SECRET_ID");
            }
            return secretId.trim();
        }

        public String requireSecretKey() {
            if (secretKey == null || secretKey.isBlank()) {
                throw new IllegalStateException("未配置 APP_MAIL_SES_API_SECRET_KEY");
            }
            return secretKey.trim();
        }

        public String resolveRegion() {
            return (region == null || region.isBlank()) ? "ap-guangzhou" : region.trim();
        }

        public String resolveEndpoint() {
            return (endpoint == null || endpoint.isBlank()) ? "ses.tencentcloudapi.com" : endpoint.trim();
        }

        public long resolveTriggerType() {
            return triggerType == null ? 1L : triggerType;
        }

        public long requireVerifyEmailTemplateId() {
            return requireTemplateId(verifyEmailTemplateId, "APP_MAIL_SES_API_VERIFY_EMAIL_TEMPLATE_ID");
        }

        public long requireResetPasswordTemplateId() {
            return requireTemplateId(resetPasswordTemplateId, "APP_MAIL_SES_API_RESET_PASSWORD_TEMPLATE_ID");
        }

        private long requireTemplateId(Long templateId, String envName) {
            if (templateId == null || templateId <= 0) {
                throw new IllegalStateException("未配置 " + envName);
            }
            return templateId;
        }
    }
}
