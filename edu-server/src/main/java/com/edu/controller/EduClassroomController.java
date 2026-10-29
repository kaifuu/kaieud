package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduClassroom;
import com.edu.entity.EduSchedule;
import com.edu.mapper.EduClassroomMapper;
import com.edu.mapper.EduScheduleMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 教室管理：教室与班级/课次为1对多；支持占用/空余时段查询 */
@RestController
@RequestMapping("/edu/classroom")
@RequiredArgsConstructor
@RequiresPerm("edu:classroom")
public class EduClassroomController {

    private final EduClassroomMapper classroomMapper;
    private final EduScheduleMapper scheduleMapper;

    @GetMapping("/page")
    public Result<PageResult<EduClassroom>> page(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Long orgId) {
        LambdaQueryWrapper<EduClassroom> qw = new LambdaQueryWrapper<EduClassroom>()
                .like(StringUtils.hasText(keyword), EduClassroom::getName, keyword)
                .eq(orgId != null, EduClassroom::getOrgId, orgId)
                .eq(EduClassroom::getDeleted, 0)
                .orderByDesc(EduClassroom::getId);
        Page<EduClassroom> p = classroomMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public Result<?> list() {
        return Result.ok(classroomMapper.selectList(new LambdaQueryWrapper<EduClassroom>()
                .eq(EduClassroom::getDeleted, 0).orderByAsc(EduClassroom::getId)));
    }

    @PostMapping
    @Log("新增教室")
    public Result<Void> save(@RequestBody EduClassroom room) {
        room.setTenantId(AuthContext.get().getTenantId());
        room.setDeleted(0);
        classroomMapper.insert(room);
        return Result.ok();
    }

    @PutMapping
    @Log("修改教室")
    public Result<Void> update(@RequestBody EduClassroom room) {
        classroomMapper.updateById(room);
        return Result.ok();
    }

    /** 删除教室：有课次引用（按教室ID）则只允许下线，这里直接逻辑删除并保留历史 */
    @DeleteMapping("/{id}")
    @Log("删除教室")
    public Result<Void> delete(@PathVariable Long id) {
        Long refs = scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getClassroomId, id).ne(EduSchedule::getStatus, 2));
        if (refs > 0) return Result.error("该教室已排课 " + refs + " 次，不能删除，可编辑后停用");
        EduClassroom r = classroomMapper.selectById(id);
        if (r != null) { r.setDeleted(1); classroomMapper.updateById(r); }
        return Result.ok();
    }

    /**
     * 教室占用/空余时段查询：按天给出 08:00-22:00 内已占用与空余时间段。
     * occupied: [{date, start, end}]；free: [{date, start, end}]
     */
    @GetMapping("/{id}/occupancy")
    public Result<Map<String, Object>> occupancy(@PathVariable Long id,
                                                 @RequestParam String begin,
                                                 @RequestParam String end) {
        EduClassroom room = classroomMapper.selectById(id);
        if (room == null) return Result.error("教室不存在");

        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getClassroomId, id)
                .ne(EduSchedule::getStatus, 2)
                .ge(EduSchedule::getStartTime, begin + " 00:00:00")
                .le(EduSchedule::getEndTime, end + " 23:59:59")
                .orderByAsc(EduSchedule::getStartTime);
        List<EduSchedule> occ = scheduleMapper.selectList(qw);

        List<Map<String, Object>> occupied = new ArrayList<>();
        for (EduSchedule s : occ) {
            Map<String, Object> m = new HashMap<>();
            m.put("date", s.getStartTime().toLocalDate().toString());
            m.put("start", s.getStartTime().toLocalTime().toString().substring(0, 5));
            m.put("end", s.getEndTime().toLocalTime().toString().substring(0, 5));
            m.put("status", s.getStatus());
            occupied.add(m);
        }

        // 空余时段：每天 08:00-22:00 减去已占用
        List<Map<String, Object>> free = new ArrayList<>();
        LocalDate d = LocalDate.parse(begin);
        LocalDate endD = LocalDate.parse(end);
        while (!d.isAfter(endD)) {
            List<int[]> segs = new ArrayList<>();
            segs.add(new int[]{8 * 60, 22 * 60});
            for (EduSchedule s : occ) {
                if (!s.getStartTime().toLocalDate().equals(d)) continue;
                int os = s.getStartTime().getHour() * 60 + s.getStartTime().getMinute();
                int oe = s.getEndTime().getHour() * 60 + s.getEndTime().getMinute();
                List<int[]> next = new ArrayList<>();
                for (int[] seg : segs) {
                    if (oe <= seg[0] || os >= seg[1]) { next.add(seg); continue; }
                    if (os > seg[0]) next.add(new int[]{seg[0], os});
                    if (oe < seg[1]) next.add(new int[]{oe, seg[1]});
                }
                segs = next;
            }
            for (int[] seg : segs) {
                if (seg[1] - seg[0] < 30) continue;
                Map<String, Object> m = new HashMap<>();
                m.put("date", d.toString());
                m.put("start", String.format("%02d:%02d", seg[0] / 60, seg[0] % 60));
                m.put("end", String.format("%02d:%02d", seg[1] / 60, seg[1] % 60));
                free.add(m);
            }
            d = d.plusDays(1);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("room", room);
        data.put("occupied", occupied);
        data.put("free", free);
        return Result.ok(data);
    }
}
