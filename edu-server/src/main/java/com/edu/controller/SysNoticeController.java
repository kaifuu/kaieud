package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.SysDict;
import com.edu.entity.SysNotice;
import com.edu.entity.SysNoticeRead;
import com.edu.mapper.SysNoticeReadMapper;
import com.edu.entity.SysRole;
import com.edu.entity.SysUser;
import com.edu.entity.SysUserRole;
import com.edu.mapper.SysDictMapper;
import com.edu.mapper.SysNoticeMapper;
import com.edu.mapper.SysRoleMapper;
import com.edu.mapper.SysUserMapper;
import com.edu.mapper.SysUserRoleMapper;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 通知管理：通知列表（按接收人过滤——角色/指定用户）+ 右上角铃铛 + 消息历史（全量只读）。
 * 可见性：已发布 且 (接收角色含我的任一角色 或 指定用户含我)；本人创建的（含草稿）始终可见，
 * 保证"A教务的审批消息只有A教务看得到，B教务看不到"。
 */
@RestController
@RequestMapping("/sys/notice")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeMapper noticeMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;
    private final SysDictMapper dictMapper;
    private final SysNoticeReadMapper readMapper;

    /**
     * 通知列表分页（通知管理TAB1）：本租户通知 + 平台公告(tenant_id=0) 中我能收到的 + 我创建的。
     * 全局查询绕过租户拦截器（平台公告对所有企业可见）。登录即可查。
     */
    @GetMapping("/page")
    public Result<PageResult<SysNotice>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer noticeType,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String sortBy,
                                              @RequestParam(required = false) String direction) {
        LoginUser lu = AuthContext.get();
        Page<SysNotice> p = noticeMapper.selectGlobalPage(new Page<>(page, size),
                visibleWrapper(lu)
                        .like(StringUtils.hasText(keyword), SysNotice::getTitle, keyword)
                        .eq(noticeType != null, SysNotice::getNoticeType, noticeType)
                        .eq(status != null, SysNotice::getStatus, status)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "title", "level", "status", "publishTime", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 右上角铃铛：本租户通知 + 平台公告 中我能收到的最近已发布 */
    @GetMapping("/recent")
    public Result<List<SysNotice>> recent(@RequestParam(defaultValue = "8") int limit) {
        LoginUser lu = AuthContext.get();
        List<SysNotice> list = noticeMapper.selectGlobalList(visibleWrapper(lu)
                .eq(SysNotice::getStatus, 1)
                .orderByDesc(SysNotice::getPublishTime)
                .last("LIMIT " + Math.min(limit, 20)));
        return Result.ok(list);
    }

    /**
     * 平台公告管理（平台运营TAB）：tenant_id=0 的通知（系统升级公告等），仅平台运营可管理。
     */
    @GetMapping("/platform-page")
    public Result<PageResult<SysNotice>> platformPage(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer status) {
        if (!AuthContext.get().isPlatformAdmin()) return Result.error("仅平台运营可管理平台公告");
        Page<SysNotice> p = noticeMapper.selectGlobalPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysNotice>()
                        .eq(SysNotice::getTenantId, 0L)
                        .like(StringUtils.hasText(keyword), SysNotice::getTitle, keyword)
                        .eq(status != null, SysNotice::getStatus, status)
                        .orderByDesc(SysNotice::getId));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /**
     * 消息历史（通知管理TAB3）：本租户 + 平台公告 的全部已发布记录，任何登录用户可查、只读。
     */
    @GetMapping("/history")
    public Result<PageResult<Map<String, Object>>> history(@RequestParam(defaultValue = "1") long page,
                                                           @RequestParam(defaultValue = "10") long size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) Integer noticeType,
                                                           @RequestParam(required = false) Integer level) {
        LoginUser lu = AuthContext.get();
        Long myTenant = lu.getTenantId() == null ? 0L : lu.getTenantId();
        Page<SysNotice> p = noticeMapper.selectGlobalPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysNotice>()
                        .in(SysNotice::getTenantId, java.util.Arrays.asList(0L, myTenant))
                        .eq(SysNotice::getStatus, 1)
                        .like(StringUtils.hasText(keyword), SysNotice::getTitle, keyword)
                        .eq(noticeType != null, SysNotice::getNoticeType, noticeType)
                        .eq(level != null, SysNotice::getLevel, level)
                        .orderByDesc(SysNotice::getPublishTime));
        Map<Long, String> userNames = new HashMap<>();
        List<Long> uidNeed = new ArrayList<>();
        for (SysNotice n : p.getRecords()) {
            for (String s : (n.getTargetUsers() == null ? "" : n.getTargetUsers()).split(",")) {
                if (StringUtils.hasText(s.trim())) {
                    try { uidNeed.add(Long.valueOf(s.trim())); } catch (NumberFormatException ignore) { }
                }
            }
        }
        if (!uidNeed.isEmpty())
            for (SysUser u : userMapper.selectBatchIds(uidNeed)) userNames.put(u.getId(), u.getRealName());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysNotice n : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("title", n.getTitle());
            m.put("content", n.getContent());
            m.put("noticeType", n.getNoticeType());
            m.put("level", n.getLevel());
            m.put("targetName", roleNames(n.getTarget()));
            m.put("targetUserNames", targetUserNames(n.getTargetUsers(), userNames));
            m.put("createBy", n.getCreateBy());
            m.put("publishTime", n.getPublishTime());
            rows.add(m);
        }
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    @PostMapping
    @RequiresPerm("sys:notify")
    @Log("新增/修改通知")
    public Result<Void> save(@RequestBody SysNotice notice) {
        LoginUser lu = AuthContext.get();
        notice.setTenantId(lu.getTenantId());
        if (notice.getId() == null) {
            notice.setCreateUserId(lu.getId());
            notice.setCreateBy(lu.getRealName());
        }
        // 新建即已发布 或 编辑时转为已发布：回填发布时间（列表/历史按发布时间排序展示）
        if (notice.getStatus() != null && notice.getStatus() == 1
                && notice.getPublishTime() == null) {
            notice.setPublishTime(LocalDateTime.now());
        }
        if (notice.getId() != null) noticeMapper.updateById(notice);
        else noticeMapper.insert(notice);
        return Result.ok();
    }

    /** 发布通知（仅限本租户/本平台域的通知） */
    @PostMapping("/{id}/publish")
    @RequiresPerm("sys:notify")
    @Log("发布通知")
    public Result<Void> publish(@PathVariable Long id) {
        SysNotice n = owned(id);
        if (n == null) return Result.error("通知不存在");
        n.setStatus(1);
        n.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(n);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("sys:notify")
    @Log("删除通知")
    public Result<Void> delete(@PathVariable Long id) {
        if (owned(id) == null) return Result.error("通知不存在");
        noticeMapper.deleteById(id);
        return Result.ok();
    }

    /** 归属校验：平台运营只能管平台公告(tenant 0)，企业只能管本企业通知 */
    private SysNotice owned(Long id) {
        SysNotice n = noticeMapper.selectById(id);
        if (n == null) return null;
        Long my = AuthContext.get().getTenantId() == null ? 0L : AuthContext.get().getTenantId();
        if (!my.equals(n.getTenantId() == null ? 0L : n.getTenantId()))
            throw new com.edu.common.exception.BizException("不能操作其他租户的通知");
        return n;
    }

    /* ==================== 已读/未读 ==================== */

    /** 当前用户已读的通知ID集合（前端比对展示 已读/未读 标签） */
    @GetMapping("/read-ids")
    public Result<List<Long>> readIds() {
        LoginUser lu = AuthContext.get();
        return Result.ok(readMapper.selectList(new LambdaQueryWrapper<SysNoticeRead>()
                        .eq(SysNoticeRead::getUserId, lu.getId()))
                .stream().map(SysNoticeRead::getNoticeId).collect(Collectors.toList()));
    }

    /** 未读数：我能看到的已发布通知中尚未阅读的数量（右上角铃铛角标） */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        LoginUser lu = AuthContext.get();
        List<SysNotice> visible = noticeMapper.selectGlobalList(visibleWrapper(lu)
                .eq(SysNotice::getStatus, 1));
        if (visible.isEmpty()) return Result.ok(0L);
        Set<Long> readSet = new HashSet<>(readMapper.selectList(new LambdaQueryWrapper<SysNoticeRead>()
                        .eq(SysNoticeRead::getUserId, lu.getId()))
                .stream().map(SysNoticeRead::getNoticeId).collect(Collectors.toList()));
        return Result.ok(visible.stream().filter(n -> !readSet.contains(n.getId())).count());
    }

    /** 标记已读：用户点击消息查看内容时调用（幂等） */
    @PostMapping("/read")
    public Result<Void> markRead(@RequestBody ReadDTO dto) {
        if (dto == null || dto.getId() == null) return Result.error("缺少通知ID");
        LoginUser lu = AuthContext.get();
        Long cnt = readMapper.selectCount(new LambdaQueryWrapper<SysNoticeRead>()
                .eq(SysNoticeRead::getNoticeId, dto.getId())
                .eq(SysNoticeRead::getUserId, lu.getId()));
        if (cnt == 0) {
            SysNoticeRead r = new SysNoticeRead();
            r.setNoticeId(dto.getId());
            r.setUserId(lu.getId());
            readMapper.insert(r);
        }
        return Result.ok();
    }

    /** 全部已读：把我能看到的已发布通知一次性标记为已读 */
    @PostMapping("/read-all")
    @Log("通知全部已读")
    public Result<Integer> markAllRead() {
        LoginUser lu = AuthContext.get();
        List<SysNotice> visible = noticeMapper.selectGlobalList(visibleWrapper(lu)
                .eq(SysNotice::getStatus, 1));
        Set<Long> readSet = new HashSet<>(readMapper.selectList(new LambdaQueryWrapper<SysNoticeRead>()
                        .eq(SysNoticeRead::getUserId, lu.getId()))
                .stream().map(SysNoticeRead::getNoticeId).collect(Collectors.toList()));
        int n = 0;
        for (SysNotice notice : visible) {
            if (readSet.contains(notice.getId())) continue;
            SysNoticeRead r = new SysNoticeRead();
            r.setNoticeId(notice.getId());
            r.setUserId(lu.getId());
            readMapper.insert(r);
            n++;
        }
        return Result.ok(n);
    }

    @lombok.Data
    public static class ReadDTO {
        private Long id;
    }

    /* ==================== 可见性 ==================== */

    /**
     * 我的可见范围：已发布 且 (接收角色含我的任一角色 或 接收用户类型=我的类型 或 指定用户含我)，
     * 或 我创建的（草稿也可见）。接收对象现按用户管理的用户类型（user_type 字典值）下发，
     * 历史角色编码（EDU_ADMIN 等）继续兼容。
     */
    private LambdaQueryWrapper<SysNotice> visibleWrapper(LoginUser lu) {
        List<String> roles = myRoles(lu);
        String myType = lu.getUserType() == null ? "-1" : String.valueOf(lu.getUserType());
        Long myTenant = lu.getTenantId() == null ? 0L : lu.getTenantId();
        LambdaQueryWrapper<SysNotice> qw = new LambdaQueryWrapper<>();
        qw.and(w -> {
            w.and(x -> {
                // 数据范围：本租户通知 + 平台公告（tenant_id=0，系统升级公告等对全员可见）
                x.in(SysNotice::getTenantId, java.util.Arrays.asList(0L, myTenant))
                        .eq(SysNotice::getStatus, 1).and(y -> {
                    y.apply("FIND_IN_SET('ALL', target)");   // 全员通知：任何人都可见
                    for (String r : roles) y.or().apply("FIND_IN_SET({0}, target)", r);
                    y.or().apply("FIND_IN_SET({0}, target)", myType);
                    y.or().apply("FIND_IN_SET({0}, target_users)", lu.getId());
                });
            });
            w.or().eq(SysNotice::getCreateUserId, lu.getId());
        });
        return qw;
    }

    /** 我的角色编码：取用户实际挂的角色，缺省按用户类型回退 */
    private List<String> myRoles(LoginUser lu) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, lu.getId()))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<String> codes = roleIds.isEmpty() ? new ArrayList<>()
                : roleMapper.selectBatchIds(roleIds).stream()
                    .map(SysRole::getCode).filter(StringUtils::hasText).collect(Collectors.toList());
        if (codes.isEmpty()) {
            Integer t = lu.getUserType();
            if (t != null) {
                if (t == 2) codes.add("EDU_ADMIN");
                else if (t == 3) codes.add("CLASS_TEACHER");
                else if (t == 4) codes.add("TEACHER");
                else if (t == 5) codes.add("STUDENT");
            }
        }
        if (codes.isEmpty()) codes.add("ALL"); // 超管等未挂角色：兜底可见全员通知
        return codes;
    }

    /** 接收对象转中文名：优先角色编码映射，其次用户类型字典（user_type），原样兜底 */
    private String roleNames(String target) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ALL", "全部");
        m.put("EDU_ADMIN", "教管人员");
        m.put("CLASS_TEACHER", "班主任");
        m.put("TEACHER", "教师");
        m.put("STUDENT", "学生");
        m.put("SUPER_ADMIN", "系统管理员");
        m.put("CAMPUS_PRINCIPAL", "校区校长");
        m.put("CONSULTANT", "校区咨询师");
        m.put("ACADEMIC_MANAGER", "校区学管师");
        m.put("FINANCE", "财务");
        Map<String, String> userTypes = new HashMap<>();
        for (SysDict d : dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, "user_type")))
            userTypes.put(String.valueOf(d.getValue()), d.getLabel());
        return Arrays.stream((target == null || target.isEmpty() ? "ALL" : target).split(","))
                .map(x -> m.getOrDefault(x.trim(), userTypes.getOrDefault(x.trim(), x.trim())))
                .collect(Collectors.joining("、"));
    }

    /** 指定接收用户ID串转姓名串 */
    private String targetUserNames(String targetUsers, Map<Long, String> userNames) {
        if (!StringUtils.hasText(targetUsers)) return "-";
        return Arrays.stream(targetUsers.split(","))
                .map(s -> {
                    try { return userNames.getOrDefault(Long.valueOf(s.trim()), "用户#" + s.trim()); }
                    catch (NumberFormatException e) { return "用户#" + s.trim(); }
                })
                .collect(Collectors.joining("、"));
    }
}
