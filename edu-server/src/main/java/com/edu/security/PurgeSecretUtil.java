package com.edu.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 企业数据物理清除的超级秘钥校验：
 * 秘钥不以明文落盘——yml 存 3DES(DESede/ECB/PKCS5Padding) 密文，运行时解密后与输入比对；
 * 比对使用 SHA-256 摘要恒时比较，避免时序侧信道。
 */
@Component
public class PurgeSecretUtil {

    @Value("${edu.jwt.purge.crypto-key}")
    private String cryptoKey;

    @Value("${edu.jwt.purge.secret-cipher}")
    private String secretCipher;

    /** 解密出超级秘钥明文（仅内存中短暂存在） */
    private String decrypt() {
        try {
            byte[] key = cryptoKey.getBytes(StandardCharsets.UTF_8);
            if (key.length != 24) throw new IllegalStateException("3DES密钥必须为24字节");
            Cipher cipher = Cipher.getInstance("DESede/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "DESede"));
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(secretCipher));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("超级秘钥配置解密失败，请检查yml", e);
        }
    }

    /** 校验输入秘钥是否匹配（SHA-256 恒时比较） */
    public boolean matches(String input) {
        if (input == null || input.isEmpty()) return false;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] a = md.digest(input.getBytes(StandardCharsets.UTF_8));
            byte[] b = MessageDigest.getInstance("SHA-256").digest(decrypt().getBytes(StandardCharsets.UTF_8));
            return MessageDigest.isEqual(a, b);
        } catch (Exception e) {
            return false;
        }
    }
}
