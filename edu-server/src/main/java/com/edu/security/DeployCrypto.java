package com.edu.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 发布凭据加解密：目标服务器 SSH 密码/口令不以明文落库，
 * 3DES(DESede/ECB/PKCS5Padding) 加密，密钥复用 edu.jwt.purge.crypto-key（24字节）。
 */
@Component
public class DeployCrypto {

    @Value("${edu.jwt.purge.crypto-key}")
    private String cryptoKey;

    public String encrypt(String plain) {
        try {
            Cipher c = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE, key());
            return Base64.getEncoder().encodeToString(c.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("凭据加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            Cipher c = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            c.init(Cipher.DECRYPT_MODE, key());
            return new String(c.doFinal(Base64.getDecoder().decode(cipherText)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("凭据解密失败（密钥是否变更？）", e);
        }
    }

    private SecretKeySpec key() {
        byte[] k = cryptoKey.getBytes(StandardCharsets.UTF_8);
        if (k.length != 24) throw new IllegalStateException("3DES密钥必须为24字节");
        return new SecretKeySpec(k, "DESede");
    }
}
