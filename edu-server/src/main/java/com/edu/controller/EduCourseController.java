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
    private final com.edu.mapper.EduCourseCampusMapper courseCampusMapper;
    private final com.edu.mapper.SysDictMapper dictMapper;

    @GetMapping("/page")
    public Result<PageResult<EduCourse>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer category,
                                              @RequestParam(required = false) Integer classType,
                                              @RequestParam(required = false) Integer teachMode,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String campus,
                                              @RequestParam(required = false) String chargeMode,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<EduCourse> qw = new LambdaQueryWrapper<EduCourse>()
                .like(StringUtils.hasText(keyword), EduCourse::getName, keyword)
                .eq(category != null, EduCourse::getCategory, category)
                .eq(classType != null, EduCourse::getClassType, classType)
                .eq(teachMode != null, EduCourse::getTeachMode, teachMode)
                .eq(status != null, EduCourse::getStatus, status)
                // 收费模式筛选：hour=按课时(chargeHour=1) / time=按时间(chargeTime=1)
                .eq("hour".equals(chargeMode), EduCourse::getChargeHour, 1)
                .eq("time".equals(chargeMode), EduCourse::getChargeTime, 1);
        // 校区筛选：命中中间表任一关联校区即返回（列表仍以课程维度一条呈现）
        if (StringUtils.hasText(campus))
            qw.apply("EXISTS (SELECT 1 FROM edu_course_campus cc WHERE cc.course_id = edu_course.id AND cc.campus = {0})", campus);
        if (StringUtils.hasText(sortBy)) {
            // 指定排序：白名单校验后拼接（与默认排序互斥，避免双 ORDER BY）
            qw.last(SafeSort.order(sortBy, direction, "id", "id", "name", "category",
                    "teachMode", "grade", "status", "createTime", "updateTime"));
        } else {
            // 默认按更新时间降序（最近维护的课程在前）
            qw.orderByDesc(EduCourse::getUpdateTime).orderByDesc(EduCourse::getId);
        }
        Page<EduCourse> p = courseMapper.selectPage(new Page<>(page, size), qw);
        fillCampuses(p.getRecords());
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 上架课程列表（学生选课中心也用） */
    @GetMapping("/list")
    public Result<?> list() {
        java.util.List<EduCourse> list = courseMapper.selectList(
                new LambdaQueryWrapper<EduCourse>().eq(EduCourse::getStatus, 1));
        fillCampuses(list);
        return Result.ok(list);
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
        saveCampuses(course);
        return Result.ok();
    }

    @PutMapping
    @Log("修改课程")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@RequestBody EduCourse course) {
        // 仅完整表单保存（显式提交 campuses）时才重写中间表；
        // 局部更新（如上下架仅传 id/status）不动课程-校区关联
        if (course.getCampuses() != null) saveCampuses(course);
        courseMapper.updateById(course);
        savePrices(course.getId(), course.getPrices());
        return Result.ok();
    }

    /** 当前租户的校区字典（不含"全部校区"） */
    private java.util.List<String> dictCampuses() {
        return dictMapper.selectList(new LambdaQueryWrapper<com.edu.entity.SysDict>()
                        .eq(com.edu.entity.SysDict::getTenantId, AuthContext.get().getTenantId())
                        .eq(com.edu.entity.SysDict::getDictType, "campus")
                        .ne(com.edu.entity.SysDict::getLabel, "全部校区")
                        .orderByAsc(com.edu.entity.SysDict::getSort))
                .stream().map(com.edu.entity.SysDict::getLabel)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 课程-校区关联全量保存（先删后插）：中间表为多对多数据源。
     * 选择"全部校区"（campuses 为空）时按当期校区字典物化为每个校区一条关联记录，
     * 后续字典新增校区不会自动扩散；历史字段 campus/campus_mode 同步写逗号串保持兼容。
     */
    private void saveCampuses(EduCourse course) {
        java.util.List<String> campuses = course.getCampuses() == null
                ? new java.util.ArrayList<>() : new java.util.ArrayList<>(course.getCampuses());
        campuses.removeIf(c -> c == null || c.trim().isEmpty() || "全部校区".equals(c.trim()));
        if (campuses.isEmpty()) campuses = dictCampuses();
        if (campuses.isEmpty()) campuses = java.util.Collections.singletonList("全部校区");
        courseCampusMapper.delete(new LambdaQueryWrapper<com.edu.entity.EduCourseCampus>()
                .eq(com.edu.entity.EduCourseCampus::getCourseId, course.getId()));
        for (String c : campuses) {
            com.edu.entity.EduCourseCampus cc = new com.edu.entity.EduCourseCampus();
            cc.setTenantId(AuthContext.get().getTenantId());
            cc.setCourseId(course.getId());
            cc.setCampus(c);
            courseCampusMapper.insert(cc);
        }
        // 历史字段兼容：逗号串 + 指定模式
        course.setCampus(String.join(",", campuses));
        course.setCampusMode(2);
        course.setCampuses(campuses);
    }

    /** 批量填充课程的校区列表（按中间表聚合，列表/详情统一数据源） */
    private void fillCampuses(java.util.List<EduCourse> courses) {
        if (courses == null || courses.isEmpty()) return;
        java.util.List<Long> ids = courses.stream().map(EduCourse::getId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toList());
        if (ids.isEmpty()) return;
        java.util.Map<Long, java.util.List<String>> map = new java.util.HashMap<>();
        for (com.edu.entity.EduCourseCampus cc : courseCampusMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduCourseCampus>()
                        .in(com.edu.entity.EduCourseCampus::getCourseId, ids)
                        .orderByAsc(com.edu.entity.EduCourseCampus::getId)))
            map.computeIfAbsent(cc.getCourseId(), k -> new java.util.ArrayList<>()).add(cc.getCampus());
        courses.forEach(c -> c.setCampuses(map.getOrDefault(c.getId(), new java.util.ArrayList<>())));
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
        courseCampusMapper.delete(new LambdaQueryWrapper<com.edu.entity.EduCourseCampus>()
                .eq(com.edu.entity.EduCourseCampus::getCourseId, id));
        courseMapper.deleteById(id);
        return Result.ok();
    }
}
