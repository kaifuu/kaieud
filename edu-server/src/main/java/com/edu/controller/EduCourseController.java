package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduCourse;
import com.edu.mapper.EduCourseMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/** 课程(班型)管理 */
@RestController
@RequestMapping("/edu/course")
@RequiredArgsConstructor
@RequiresPerm("edu:course")
public class EduCourseController {

    private final EduCourseMapper courseMapper;
    private final com.edu.mapper.EduPackageMapper packageMapper;

    @GetMapping("/page")
    public Result<PageResult<EduCourse>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer category,
                                              @RequestParam(required = false) Integer classType,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<EduCourse> p = courseMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduCourse>()
                        .like(StringUtils.hasText(keyword), EduCourse::getName, keyword)
                        .eq(category != null, EduCourse::getCategory, category)
                        .eq(classType != null, EduCourse::getClassType, classType)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "name", "courseType", "price", "discount", "totalHours", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 上架课程列表（学生选课中心也用） */
    @GetMapping("/list")
    public Result<?> list() {
        return Result.ok(courseMapper.selectList(
                new LambdaQueryWrapper<EduCourse>().eq(EduCourse::getStatus, 1)));
    }

    @PostMapping
    @Log("新增课程")
    public Result<Void> save(@RequestBody EduCourse course) {

        // 页面已精简定价字段：缺省值兜底（存量非空列）
        if (course.getTotalHours() == null) course.setTotalHours(0);
        if (course.getPrice() == null) course.setPrice(java.math.BigDecimal.ZERO);
        if (course.getDiscount() == null) course.setDiscount(java.math.BigDecimal.valueOf(100));
        course.setTenantId(AuthContext.get().getTenantId());
        courseMapper.insert(course);
        return Result.ok();
    }

    @PutMapping
    @Log("修改课程")
    public Result<Void> update(@RequestBody EduCourse course) {
        courseMapper.updateById(course);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除课程")
    public Result<Void> delete(@PathVariable Long id) {
        // 已绑定套餐的课程不能删除（保护套餐/合同引用完整性），只能下架停售
        long bound = packageMapper.selectCount(new LambdaQueryWrapper<com.edu.entity.EduPackage>()
                .eq(com.edu.entity.EduPackage::getCourseId, id)
                .eq(com.edu.entity.EduPackage::getDeleted, 0));
        if (bound > 0)
            return Result.error("该课程已绑定 " + bound + " 个课时套餐，不能删除，只能下架停售");
        courseMapper.deleteById(id);
        return Result.ok();
    }
}
