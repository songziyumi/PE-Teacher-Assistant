package com.pe.assistant.service;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import org.springframework.stereotype.Component;

@Component
public class DefaultTencentSesClientFactory implements TencentSesClientFactory {

    @Override
    public SesClient create(Credential credential, String region, ClientProfile clientProfile) {
        return new SesClient(credential, region, clientProfile);
    }
}
