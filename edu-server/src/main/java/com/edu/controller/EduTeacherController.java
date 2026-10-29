package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Result;
import com.edu.entity.EduSchedule;
import com.edu.entity.EduTeacher;
import com.edu.mapper.EduScheduleMapper;
import com.edu.mapper.EduTeacherMapper;
import com.edu.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 教师档案：仅保留下拉列表与多教师空闲查询（档案随用户管理建档生成） */
@RestController
@RequestMapping("/edu/teacher")
@RequiredArgsConstructor
public class EduTeacherController {

    private final EduTeacherMapper teacherMapper;
    private final EduScheduleMapper scheduleMapper;

    /** 教师下拉列表（在职） */
    @GetMapping("/list")
    public Result<?> list() {
        return Result.ok(teacherMapper.selectList(new LambdaQueryWrapper<EduTeacher>()
                .eq(EduTeacher::getStatus, 1).eq(EduTeacher::getDeleted, 0)));
    }

    /**
     * 教师空闲查询：多名老师共同空闲时间段交集（PRD核心功能）。
     * 空闲定义 = 工作时段(08:00-21:00) - 固定禁排时段 - 已有排课。
     */
    @GetMapping("/free-time")
    public Result<?> freeTime(@RequestParam String begin,
                              @RequestParam String end,
                              @RequestParam("teacherIds") List<Long> teacherIds,
                              @RequestParam(required = false) Integer period) {
        if (teacherIds == null || teacherIds.isEmpty()) return Result.error("请至少选择一名教师");
        List<EduTeacher> teachers = teacherMapper.selectBatchIds(teacherIds);
        if (teachers.size() != teacherIds.size()) return Result.error("存在无效教师");

        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = LocalDate.parse(begin); !d.isAfter(LocalDate.parse(end)); d = d.plusDays(1))
            dates.add(d);

        List<EduSchedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<EduSchedule>()
                        .in(EduSchedule::getTeacherId, teacherIds)
                        .ne(EduSchedule::getStatus, 2)
                        .ge(EduSchedule::getStartTime, begin + " 00:00:00")
                        .le(EduSchedule::getStartTime, end + " 23:59:59"));
        Map<Long, List<EduSchedule>> schByTeacher = schedules.stream()
                .collect(Collectors.groupingBy(EduSchedule::getTeacherId));

        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate day : dates) {
            List<List<int[]>> freePerTeacher = new ArrayList<>();
            for (EduTeacher t : teachers) {
                List<int[]> free = new ArrayList<>();
                free.add(new int[]{8 * 60, 21 * 60});
                if (StringUtils.hasText(t.getBanTimes())) {
                    try {
                        List<Map<String, Object>> bans = om.readValue(t.getBanTimes(),
                                om.getTypeFactory().constructCollectionType(List.class, Map.class));
                        int week = day.getDayOfWeek().getValue();
                        for (Map<String, Object> b : bans) {
                            if (Integer.parseInt(String.valueOf(b.get("week"))) == week) {
                                free = subtract(free, toMin((String) b.get("start")), toMin((String) b.get("end")));
                            }
                        }
                    } catch (Exception ignore) { }
                }
                for (EduSchedule sch : schByTeacher.getOrDefault(t.getId(), new ArrayList<>())) {
                    if (sch.getStartTime().toLocalDate().equals(day)) {
                        free = subtract(free, sch.getStartTime().getHour() * 60 + sch.getStartTime().getMinute(),
                                sch.getEndTime().getHour() * 60 + sch.getEndTime().getMinute());
                    }
                }
                freePerTeacher.add(free);
            }
            List<int[]> inter = freePerTeacher.get(0);
            for (List<int[]> fr : freePerTeacher.subList(1, freePerTeacher.size())) inter = intersect(inter, fr);
            for (int[] seg : inter) {
                if (seg[1] - seg[0] < 30) continue;
                int startHour = seg[0] / 60;
                if (period != null) {
                    if (period == 1 && startHour >= 12) continue;
                    if (period == 2 && (startHour < 12 || startHour >= 18)) continue;
                    if (period == 3 && startHour < 18) continue;
                }
                Map<String, Object> item = new java.util.HashMap<>();
                item.put("date", day.toString());
                item.put("start", String.format("%02d:%02d", seg[0] / 60, seg[0] % 60));
                item.put("end", String.format("%02d:%02d", seg[1] / 60, seg[1] % 60));
                item.put("teachers", teachers.stream().map(EduTeacher::getName).collect(Collectors.toList()));
                item.put("teacherIds", teacherIds);
                result.add(item);
            }
        }
        return Result.ok(result);
    }

    private static int toMin(String hhmm) {
        String[] a = hhmm.split(":");
        return Integer.parseInt(a[0]) * 60 + (a.length > 1 ? Integer.parseInt(a[1]) : 0);
    }

    private static List<int[]> subtract(List<int[]> free, int s, int e) {
        List<int[]> out = new ArrayList<>();
        for (int[] seg : free) {
            if (e <= seg[0] || s >= seg[1]) { out.add(seg); continue; }
            if (s > seg[0]) out.add(new int[]{seg[0], s});
            if (e < seg[1]) out.add(new int[]{e, seg[1]});
        }
        return out;
    }

    private static List<int[]> intersect(List<int[]> a, List<int[]> b) {
        List<int[]> out = new ArrayList<>();
        for (int[] x : a) for (int[] y : b) {
            int s = Math.max(x[0], y[0]), e = Math.min(x[1], y[1]);
            if (e > s) out.add(new int[]{s, e});
        }
        return out;
    }
}
