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
    private final com.edu.mapper.EduCoursePriceMapper priceMapper;
    private final com.edu.mapper.EduEnrollMapper enrollMapper;
    private final com.edu.mapper.EduContractMapper contractMapper;
    private final com.edu.mapper.EduClazzMapper clazzMapper;
    private final com.edu.mapper.EduPackageMapper packageMapper;

    @GetMapping("/page")
    public Result<PageResult<EduCourse>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer category,
                                              @RequestParam(required = false) Integer classType,
                                              @RequestParam(required = false) Integer teachMode,
                                              @RequestParam(required = false) Integer status,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<EduCourse> qw = new LambdaQueryWrapper<EduCourse>()
                .like(StringUtils.hasText(keyword), EduCourse::getName, keyword)
                .eq(category != null, EduCourse::getCategory, category)
                .eq(classType != null, EduCourse::getClassType, classType)
                .eq(teachMode != null, EduCourse::getTeachMode, teachMode)
                .eq(status != null, EduCourse::getStatus, status);
        if (StringUtils.hasText(sortBy)) {
            // 指定排序：白名单校验后拼接（与默认排序互斥，避免双 ORDER BY）
            qw.last(SafeSort.order(sortBy, direction, "id", "id", "name", "category",
                    "teachMode", "grade", "status", "createTime", "updateTime"));
        } else {
            // 默认按更新时间降序（最近维护的课程在前）
            qw.orderByDesc(EduCourse::getUpdateTime).orderByDesc(EduCourse::getId);
        }
        Page<EduCourse> p = courseMapper.selectPage(new Page<>(page, size), qw);
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
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> save(@RequestBody EduCourse course) {

        // 页面已精简定价字段：缺省值兜底（存量非空列）
        if (course.getTotalHours() == null) course.setTotalHours(0);
        if (course.getPrice() == null) course.setPrice(java.math.BigDecimal.ZERO);
        if (course.getDiscount() == null) course.setDiscount(java.math.BigDecimal.valueOf(100));
        course.setTenantId(AuthContext.get().getTenantId());
        courseMapper.insert(course);
        savePrices(course.getId(), course.getPrices());
        return Result.ok();
    }

    @PutMapping
    @Log("修改课程")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@RequestBody EduCourse course) {
        courseMapper.updateById(course);
        savePrices(course.getId(), course.getPrices());
        return Result.ok();
    }

    /** 课程定价标准（收费模式表格数据） */
    @GetMapping("/{id}/prices")
    public Result<?> prices(@PathVariable Long id) {
        return Result.ok(priceMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduCoursePrice>()
                        .eq(com.edu.entity.EduCoursePrice::getCourseId, id)
                        .orderByAsc(com.edu.entity.EduCoursePrice::getChargeMode)
                        .orderByAsc(com.edu.entity.EduCoursePrice::getCampus)
                        .orderByAsc(com.edu.entity.EduCoursePrice::getSort)));
    }

    /**
     * 定价标准全量保存：先删后插。
     * 校验：模式1每校区1~5行；模式2每校区内时间段不重复。
     */
    private void savePrices(Long courseId, java.util.List<com.edu.entity.EduCoursePrice> prices) {
        priceMapper.delete(new LambdaQueryWrapper<com.edu.entity.EduCoursePrice>()
                .eq(com.edu.entity.EduCoursePrice::getCourseId, courseId));
        if (prices == null || prices.isEmpty()) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Map<String, Integer> hourRows = new java.util.HashMap<>();
        for (com.edu.entity.EduCoursePrice p : prices) {
            if (p.getCampus() == null || p.getTotalAmount() == null) continue;
            String key = p.getChargeMode() + "|" + p.getCampus();
            if (p.getChargeMode() == 1) {
                int n = hourRows.merge(p.getCampus(), 1, Integer::sum);
                if (n > 5) throw new com.edu.common.exception.BizException("校区「" + p.getCampus() + "」按课时定价最多 5 行");
                if (p.getHours() == null || p.getHours().compareTo(java.math.BigDecimal.ZERO) <= 0) throw new com.edu.common.exception.BizException("校区「" + p.getCampus() + "」存在未填写课时的定价行");
            } else if (p.getChargeMode() == 2) {
                if (p.getTimePeriod() == null) throw new com.edu.common.exception.BizException("校区「" + p.getCampus() + "」存在未选择时间段的定价行");
                if (!seen.add(key + "|" + p.getTimePeriod()))
                    throw new com.edu.common.exception.BizException("校区「" + p.getCampus() + "」时间段「" + p.getTimePeriod() + "」重复");
            } else continue;
            p.setId(null);
            p.setCourseId(courseId);
            p.setTenantId(AuthContext.get().getTenantId());
            priceMapper.insert(p);
        }
    }

    @DeleteMapping("/{id}")
    @Log("删除课程")
    public Result<Void> delete(@PathVariable Long id) {
        // 已被套餐/报名/合同/班级引用的课程不能删除，只能下架停售
        long pkgs = packageMapper.selectCount(new LambdaQueryWrapper<com.edu.entity.EduPackage>()
                .eq(com.edu.entity.EduPackage::getCourseId, id)
                .eq(com.edu.entity.EduPackage::getDeleted, 0));
        long enrolls = enrollMapper.selectCount(new LambdaQueryWrapper<com.edu.entity.EduEnroll>()
                .eq(com.edu.entity.EduEnroll::getCourseId, id));
        long contracts = contractMapper.selectCount(new LambdaQueryWrapper<com.edu.entity.EduContract>()
                .eq(com.edu.entity.EduContract::getCourseId, id)
                .eq(com.edu.entity.EduContract::getDeleted, 0));
        long clazzes = clazzMapper.selectCount(new LambdaQueryWrapper<com.edu.entity.EduClazz>()
                .eq(com.edu.entity.EduClazz::getCourseId, id));
        if (pkgs + enrolls + contracts + clazzes > 0)
            return Result.error("该课程已被套餐(" + pkgs + ")/报名(" + enrolls + ")/合同(" + contracts
                    + ")/班级(" + clazzes + ")引用，不能删除，只能下架停售");
        courseMapper.deleteById(id);
        return Result.ok();
    }
}
