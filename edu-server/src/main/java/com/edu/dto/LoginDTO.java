package com.edu.dto;

import lombok.Data;

@Data
public class LoginDTO {
    /** 企业编码（登录域）：平台运营留空或 PLATFORM，企业用户填企业编码 */
    private String tenantCode;
    private String username;
    private String password;
    private String captchaKey;
    private String captchaCode;
}
