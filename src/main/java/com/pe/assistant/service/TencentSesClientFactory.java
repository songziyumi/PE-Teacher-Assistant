package com.pe.assistant.service;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;

public interface TencentSesClientFactory {

    SesClient create(Credential credential, String region, ClientProfile clientProfile);
}
