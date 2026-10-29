package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.entity.EduStudent;
import com.edu.mapper.EduStudentMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/** 数据导入：前端解析xlsx后批量提交学员，后端逐行校验返回成功/失败清单 */
@RestController
@RequestMapping("/sys/import")
@RequiredArgsConstructor
@RequiresPerm("sys:import")
public class DataImportController {

    private final EduStudentMapper studentMapper;

    @Data
    public static class ImportRow {
        private String name;
        private Integer gender;
        private String grade;
        private String school;
        private String parentPhone;
        private String advisor;
    }

    @Data
    public static class ImportResult {
        private int success;
        private List<String> failed = new ArrayList<>();
    }

    @PostMapping("/students")
    @Log("导入学员数据")
    public Result<ImportResult> students(@RequestBody List<ImportRow> rows) {
        ImportResult r = new ImportResult();
        Long tenantId = AuthContext.get().getTenantId();
        int i = 1;
        for (ImportRow row : rows) {
            i++;
            if (row.getName() == null || row.getName().trim().isEmpty()) {
                r.getFailed().add("第" + i + "行：学员姓名为空");
                continue;
            }
            if (row.getParentPhone() == null || row.getParentPhone().trim().isEmpty()) {
                r.getFailed().add("第" + i + "行：家长电话为空");
                continue;
            }
            Long dup = studentMapper.selectCount(new LambdaQueryWrapper<EduStudent>()
                    .eq(EduStudent::getName, row.getName())
                    .eq(EduStudent::getParentPhone, row.getParentPhone())
                    .eq(EduStudent::getDeleted, 0));
            if (dup > 0) {
                r.getFailed().add("第" + i + "行：学员已存在（姓名+家长电话重复）");
                continue;
            }
            EduStudent s = new EduStudent();
            s.setTenantId(tenantId);
            s.setName(row.getName());
            s.setGender(row.getGender() == null ? 1 : row.getGender());
            s.setGrade(row.getGrade());
            s.setSchool(row.getSchool());
            s.setParentPhone(row.getParentPhone());
            s.setAdvisor(row.getAdvisor());
            s.setStatus(1);
            s.setDeleted(0);
            studentMapper.insert(s);
            r.setSuccess(r.getSuccess() + 1);
        }
        return Result.ok(r);
    }
}
