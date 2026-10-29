package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.dto.LoginDTO;
import com.edu.dto.LoginVO;
import com.edu.dto.PasswordDTO;
import com.edu.entity.SysUser;
import com.edu.mapper.SysUserMapper;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import com.edu.service.AuthService;
import com.edu.service.CaptchaService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final SysUserMapper userMapper;

    @PostMapping("/login")
    @Log(value = "登录系统", type = 1)
    public Result<LoginVO> login(@RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto.getUsername(), dto.getPassword(),
                dto.getCaptchaKey(), dto.getCaptchaCode()));
    }

    /** 图形验证码（公开接口，无需登录） */
    @GetMapping("/captcha")
    public Result<?> captcha() {
        return Result.ok(captchaService.generate());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        AuthContext.clear();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<LoginUser> me() {
        return Result.ok(AuthContext.get());
    }

    /** 修改个人信息（姓名/性别/电话/邮箱） */
    @PutMapping("/profile")
    @Log("修改个人信息")
    public Result<Void> updateProfile(@RequestBody SysUser body) {
        SysUser user = userMapper.selectById(AuthContext.get().getId());
        if (user == null) return Result.error("用户不存在");
        user.setRealName(body.getRealName());
        user.setGender(body.getGender());
        user.setPhone(body.getPhone());
        user.setEmail(body.getEmail());
        userMapper.updateById(user);
        return Result.ok();
    }

    /** 当前用户最新菜单树（侧栏动态刷新用） */
    @GetMapping("/menus")
    public Result<Object> menus() {
        LoginUser lu = AuthContext.get();
        return Result.ok(authService.buildMenus(lu.getId(), lu.getUserType()));
    }

    @PostMapping("/password")
    @Log("修改密码")
    public Result<Void> changePassword(@RequestBody PasswordDTO dto) {
        LoginUser lu = AuthContext.get();
        SysUser user = userMapper.selectById(lu.getId());
        String old = DigestUtils.md5DigestAsHex((dto.getOldPassword() + AuthService.SALT).getBytes());
        if (!old.equals(user.getPassword())) return Result.error("原密码错误");
        user.setPassword(DigestUtils.md5DigestAsHex((dto.getNewPassword() + AuthService.SALT).getBytes()));
        userMapper.updateById(user);
        return Result.ok();
    }
}
