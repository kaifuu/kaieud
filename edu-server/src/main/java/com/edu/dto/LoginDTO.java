package com.edu.dto;

import lombok.Data;

@Data
public class LoginDTO {
    private String username;
    private String password;
    private String captchaKey;
    private String captchaCode;
}
