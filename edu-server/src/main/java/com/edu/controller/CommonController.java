package com.edu.controller;

import com.edu.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 通用文件上传：PDF/图片，单文件上限10M，保存到本地uploads目录 */
@RestController
@RequestMapping("/common")
public class CommonController {

    @Value("${edu.upload-dir:./uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) return Result.error("文件为空");
        String orig = file.getOriginalFilename();
        String ext = orig == null || !orig.contains(".")
                ? "" : orig.substring(orig.lastIndexOf('.')).toLowerCase();
        // 分类限制：作业附件 视频≤500M / 录音≤50M / 其它（PDF、图片、xlsx）≤10M
        long max;
        if (".mp4".equals(ext)) max = 500L * 1024 * 1024;
        else if (".mp3".equals(ext)) max = 50L * 1024 * 1024;
        else max = 10L * 1024 * 1024;
        if (file.getSize() > max) return Result.error("单文件不能超过 " + (max / 1024 / 1024) + "M");
        if (!".pdf".equals(ext) && !".jpg".equals(ext) && !".jpeg".equals(ext)
                && !".png".equals(ext) && !".gif".equals(ext) && !".xlsx".equals(ext)
                && !".mp4".equals(ext) && !".mp3".equals(ext))
            return Result.error("仅支持 PDF、图片、xlsx、mp4、mp3 文件");
        // 相对路径统一锚定到进程工作目录，避免被解析到 Tomcat 临时目录
        File dir = new File(uploadDir);
        if (!dir.isAbsolute()) dir = new File(System.getProperty("user.dir"), uploadDir);
        if (!dir.exists()) dir.mkdirs();
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        File target = new File(dir, name);
        file.transferTo(target.getAbsoluteFile());
        Map<String, String> r = new HashMap<>();
        r.put("url", "/uploads/" + name);
        r.put("name", orig);
        return Result.ok(r);
    }
}
