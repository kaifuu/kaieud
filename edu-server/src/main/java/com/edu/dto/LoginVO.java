package com.edu.dto;

import com.edu.security.LoginUser;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
public class LoginVO {
    private String token;
    private LoginUser user;
    private Set<String> perms;
    /** 菜单树（前端动态路由用） */
    private List<MenuNode> menus;
}
