package com.hospital.pharmacy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class HisApiKeyService {

    @Value("${app.his.api-key:his-demo-key}")
    private String configuredApiKey;

    public void requireValid(String suppliedApiKey) {
        if (!StringUtils.hasText(suppliedApiKey) || !suppliedApiKey.equals(configuredApiKey)) {
            throw new SecurityException("HIS接口密钥无效");
        }
    }
}
