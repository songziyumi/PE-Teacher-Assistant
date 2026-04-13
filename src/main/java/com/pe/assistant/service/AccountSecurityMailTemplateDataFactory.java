package com.pe.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AccountSecurityMailTemplateDataFactory {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AccountSecurityMailTemplateDataFactory() {
    }

    public static String buildVerifyEmailTemplateData(String verifyToken, int expireMinutes, String productName) {
        return toJson(Map.of(
                "verifyToken", verifyToken,
                "expireMinutes", String.valueOf(Math.max(expireMinutes, 1)),
                "productName", normalizeProductName(productName)
        ));
    }

    public static String buildResetPasswordTemplateData(String resetToken, int expireMinutes, String productName) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("resetToken", resetToken);
        values.put("expireMinutes", String.valueOf(Math.max(expireMinutes, 1)));
        values.put("productName", normalizeProductName(productName));
        return toJson(values);
    }

    private static String toJson(Map<String, String> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("构建邮件模板数据失败", exception);
        }
    }

    private static String normalizeProductName(String productName) {
        return productName == null || productName.isBlank() ? "体育教师助手" : productName.trim();
    }
}
