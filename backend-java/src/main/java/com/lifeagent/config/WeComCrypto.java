package com.lifeagent.config;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * 企业微信回调加解密。
 *
 * <p>基于官方算法实现：SHA-1 签名验证、AES-256-CBC 加解密、receiveId 校验。
 * 使用 JDK 内置 Crypto API，不引入额外依赖。</p>
 */
@Slf4j
public class WeComCrypto {

    /** AES 密钥长度：256 位 */
    private static final int AES_KEY_SIZE = 32;
    /** AES IV 长度 */
    private static final int IV_SIZE = 16;
    /** WeChat 协议随机前缀长度 */
    private static final int RANDOM_SIZE = 16;
    /** WeChat 要求 PKCS#7 填充到 32 字节倍数 */
    private static final int WECOM_PADDING_BLOCK = 32;

    private final byte[] aesKey;
    private final SecretKeySpec keySpec;
    private final IvParameterSpec ivSpec;
    private final String token;

    /**
     * @param encodingAesKey 43 字符的 Base64 EncodingAESKey
     * @param token          回调 token
     * @throws IllegalArgumentException key 格式非法
     */
    public WeComCrypto(String encodingAesKey, String token) {
        this.token = token;
        try {
            // 43 字符 + "=" = 44 字符 RFC 4648 标准 Base64，解码得 32 字节 AES-256 密钥
            byte[] decoded = java.util.Base64.getDecoder().decode(encodingAesKey + "=");
            if (decoded.length != AES_KEY_SIZE) {
                // 尝试不加 "=" 的 MIME 解码（兼容某些 SDK 生成的 key）
                decoded = java.util.Base64.getMimeDecoder().decode(encodingAesKey);
                if (decoded.length < AES_KEY_SIZE) {
                    throw new IllegalArgumentException(
                            "EncodingAESKey 解码后长度应为 " + AES_KEY_SIZE + " 字节，实际为 " + decoded.length);
                }
                decoded = Arrays.copyOf(decoded, AES_KEY_SIZE);
            }
            this.aesKey = decoded;
            this.keySpec = new SecretKeySpec(this.aesKey, "AES");
            this.ivSpec = new IvParameterSpec(Arrays.copyOfRange(this.aesKey, 0, IV_SIZE));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("EncodingAESKey 格式非法", e);
        }
    }

    /**
     * 验证签名。
     *
     * @param signature 企业微信传入的 msg_signature
     * @param timestamp 时间戳字符串
     * @param nonce     随机数
     * @param encrypt   密文
     * @return 签名是否匹配
     */
    public boolean verifySignature(String signature, String timestamp, String nonce, String encrypt) {
        String expected = generateSignature(timestamp, nonce, encrypt);
        return expected.equals(signature);
    }

    /**
     * 生成 SHA-1 签名：对 token, timestamp, nonce, encrypt 字典序排序后拼接并哈希。
     */
    public String generateSignature(String timestamp, String nonce, String encrypt) {
        try {
            String[] arr = {token, timestamp, nonce, encrypt};
            Arrays.sort(arr);
            StringBuilder sb = new StringBuilder();
            for (String s : arr) {
                sb.append(s);
            }
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("签名生成失败", e);
        }
    }

    /**
     * AES-256-CBC 解密，使用 PKCS#7 填充到 32 字节倍数（企业微信协议要求）。
     *
     * @param encrypt 密文（Base64 编码），允许含折叠空白
     * @return 解密后已去除填充的明文
     */
    public byte[] decrypt(String encrypt) {
        try {
            byte[] encryptedBytes = java.util.Base64.getMimeDecoder().decode(encrypt);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] plaintext = cipher.doFinal(encryptedBytes);
            return removeWeComPadding(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("AES 解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * AES-256-CBC 加密，按企业微信协议补齐到 32 字节倍数后使用 PKCS#7 填充。
     *
     * @param plaintext 明文
     * @return 密文（Base64 编码）
     */
    public String encrypt(byte[] plaintext) {
        try {
            byte[] padded = addWeComPadding(plaintext);
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(padded);
            return java.util.Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * PKCS#7 填充：补齐到 {@link #WECOM_PADDING_BLOCK} 字节倍数。
     * 填充值 = 需要补充的字节数（1～{@code WECOM_PADDING_BLOCK}）。
     */
    private static byte[] addWeComPadding(byte[] data) {
        int padValue = WECOM_PADDING_BLOCK - (data.length % WECOM_PADDING_BLOCK);
        byte[] padded = Arrays.copyOf(data, data.length + padValue);
        Arrays.fill(padded, data.length, padded.length, (byte) padValue);
        return padded;
    }

    /**
     * 移除 PKCS#7 填充，校验填充值合法且一致。
     *
     * @throws RuntimeException 填充格式非法
     */
    private static byte[] removeWeComPadding(byte[] data) {
        int padValue = data[data.length - 1] & 0xff;
        if (padValue < 1 || padValue > WECOM_PADDING_BLOCK) {
            throw new RuntimeException("填充值非法: " + padValue);
        }
        for (int i = data.length - padValue; i < data.length; i++) {
            if ((data[i] & 0xff) != padValue) {
                throw new RuntimeException("PKCS#7 填充校验失败");
            }
        }
        return Arrays.copyOf(data, data.length - padValue);
    }

    /**
     * 解密并解析出 XML 正文和 receiveId。
     *
     * @param encrypt   Base64 密文
     * @return 包含正文和 receiveId 的解析结果
     * @throws IllegalArgumentException receiveId 不匹配或格式非法
     */
    public DecryptResult decryptAndParse(String encrypt) {
        byte[] plaintext = decrypt(encrypt);
        // 格式：16 随机字节 + 4 字节网络序内容长度 + XML 内容 + receiveId
        ByteBuffer buffer = ByteBuffer.wrap(plaintext);
        // 跳过 16 字节随机
        buffer.position(RANDOM_SIZE);
        int contentLength = buffer.getInt();
        if (contentLength <= 0 || contentLength > buffer.remaining()) {
            throw new IllegalArgumentException("解密内容长度非法: " + contentLength);
        }
        byte[] contentBytes = new byte[contentLength];
        buffer.get(contentBytes);
        String content = new String(contentBytes, StandardCharsets.UTF_8);
        byte[] remaining = new byte[buffer.remaining()];
        buffer.get(remaining);
        String receiveId = new String(remaining, StandardCharsets.UTF_8);
        return new DecryptResult(content, receiveId);
    }

    /**
     * 加密 XML 正文。
     *
     * @param content   明文 XML
     * @param receiveId CorpID
     * @return Base64 密文
     */
    public String encryptContent(String content, String receiveId) {
        try {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            byte[] receiveIdBytes = receiveId.getBytes(StandardCharsets.UTF_8);

            int totalLength = RANDOM_SIZE + 4 + contentBytes.length + receiveIdBytes.length;
            ByteBuffer buffer = ByteBuffer.allocate(totalLength);

            // 16 字节随机
            byte[] random = new byte[RANDOM_SIZE];
            new SecureRandom().nextBytes(random);
            buffer.put(random);

            // 4 字节内容长度（网络序）
            buffer.putInt(contentBytes.length);
            // XML 内容
            buffer.put(contentBytes);
            // receiveId
            buffer.put(receiveIdBytes);

            return encrypt(buffer.array());
        } catch (Exception e) {
            throw new RuntimeException("AES 加密失败", e);
        }
    }

    /**
     * 解密结果。
     */
    public record DecryptResult(String content, String receiveId) {
    }
}
