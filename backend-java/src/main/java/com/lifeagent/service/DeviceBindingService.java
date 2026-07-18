package com.lifeagent.service;

import com.lifeagent.common.exception.ForbiddenException;
import com.lifeagent.common.exception.UnauthorizedException;
import com.lifeagent.entity.DeviceBindingEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 设备凭证校验与绑定服务接口。
 */
public interface DeviceBindingService {

    /**
     * 校验设备 ID 和 Token，返回已激活的设备绑定实体。
     *
     * @throws UnauthorizedException 设备 ID 或 Token 缺失
     * @throws ForbiddenException    凭证错误或设备已撤销
     */
    DeviceBindingEntity authenticate(String deviceId, String token);

    /**
     * 对 UTF-8 编码的 Token 计算 SHA-256 十六进制摘要。
     */
    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }
}
