package com.edu.controller;

import com.edu.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 头像读取：<img>标签无法携带JWT，故开放只读访问（仅图像内容） */
@RestController
@RequiredArgsConstructor
public class UserAvatarController {

    private final com.edu.mapper.SysUserMapper userMapper;

    @GetMapping("/sys/user/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable Long id) {
        java.util.Map<String, Object> row = userMapper.selectAvatarRaw(id);
        byte[] data = row == null ? null : (byte[]) row.get("avatar");
        if (data == null || data.length == 0) {
            return ResponseEntity.notFound().build();
        }
        // 魔数判断图片类型：PNG(89 50) / JPEG(-1 40)
        MediaType type = (data.length > 1 && data[0] == (byte) 0x89 && data[1] == 0x50)
                ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).body(data);
    }
}
