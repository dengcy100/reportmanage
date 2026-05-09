package com.report.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class DataSourcePasswordCipher {

    private static final String PREFIX = "v1:";
    private static final String AES_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    @Value("${report.datasource.crypto-key:}")
    private String cryptoKey;

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec secretKeySpec;

    @PostConstruct
    public void init() {
        if (cryptoKey == null || cryptoKey.trim().isEmpty()) {
            throw new IllegalStateException("report.datasource.crypto-key is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(cryptoKey.trim().getBytes(StandardCharsets.UTF_8));
            this.secretKeySpec = new SecretKeySpec(keyBytes, AES_ALGORITHM);
        } catch (Exception ex) {
            throw new IllegalStateException("初始化数据源密码加密器失败: " + ex.getMessage(), ex);
        }
    }

    public String encrypt(String plainText) {
        String text = plainText == null ? "" : plainText;
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.getEncoder().encodeToString(iv)
                    + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("加密数据源密码失败: " + ex.getMessage(), ex);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.trim().isEmpty()) {
            return "";
        }
        String value = cipherText.trim();
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        try {
            String payload = value.substring(PREFIX.length());
            int splitIndex = payload.indexOf(':');
            if (splitIndex <= 0 || splitIndex >= payload.length() - 1) {
                throw new IllegalStateException("密文格式不正确");
            }
            byte[] iv = Base64.getDecoder().decode(payload.substring(0, splitIndex));
            byte[] encrypted = Base64.getDecoder().decode(payload.substring(splitIndex + 1));
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("解密数据源密码失败: " + ex.getMessage(), ex);
        }
    }
}
