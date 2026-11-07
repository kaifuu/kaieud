package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BizException;
import com.edu.entity.*;
import com.edu.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 租户开通（企业级数据隔离）：
 * 每个租户拥有自己的 组织/用户/角色/菜单/字典 —— 平台保留模板，
 * 开通租户时复制平台模板（菜单树 + 角色 + 角色菜单映射 + 系统字典），
 * 并把该租户既有用户绑定的平台角色换绑为租户角色，最后生成租户管理员账号。
 */
@Service
@RequiredArgsConstructor
public class TenantProvisionService {

    /** 平台模板菜单中租户不可见的功能（租户管理本身） */
    private static final String EXCLUDE_PERM = "sys:tenant";

    private final SysTenantMapper tenantMapper;
    private final SysMenuMapper menuMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserMapper userMapper;
    private final SysDictMapper dictMapper;
    private final SysDictTypeMapper dictTypeMapper;
    private final SysPurgeLogMapper purgeLogMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 开通租户（幂等）：复制平台菜单模板；角色以租户1（历史平台角色）为模板源 */
    @Transactional
    public void provision(Long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) throw new BizException("租户不存在");

        // 1. 复制平台菜单模板（tenant_id=0 且 scope=0 的租户模板，不含平台专属菜单），保持树形结构
        if (menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, tenantId)) == 0) {
            Map<Long, Long> menuMap = new HashMap<>();
            List<SysMenu> templates = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getTenantId, 0L).eq(SysMenu::getScope, 0)
                    .orderByAsc(SysMenu::getId));
            for (SysMenu m : templates) {
                if (EXCLUDE_PERM.equals(m.getPerm())) continue;
                SysMenu copy = new SysMenu();
                copy.setTenantId(tenantId);
                copy.setParentId(m.getParentId() == null || m.getParentId() == 0 ? 0L
                        : menuMap.getOrDefault(m.getParentId(), 0L));
                copy.setName(m.getName());
                copy.setPath(m.getPath());
                copy.setComponent(m.getComponent());
                copy.setIcon(m.getIcon());
                copy.setPerm(m.getPerm());
                copy.setType(m.getType());
                copy.setSort(m.getSort());
                copy.setVisible(m.getVisible());
                menuMapper.insert(copy);
                menuMap.put(m.getId(), copy.getId());
            }
        }

        // 2. 系统字典：从平台模板(租户0)按「类型+值」补齐缺失项（幂等；
        //    新开企业自带系统字典，已有自定义字典的租户不受影响）
        seedDicts(tenantId);

        // 3. 角色处理：历史平台角色实际归属租户1（sys_role.tenant_id 默认1），
        //    以租户1角色为模板源；菜单引用按「类型+路径」键重映射到本租户菜单（幂等）
        List<SysRole> templateRoles = roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, 1L));
        Map<String, Long> tenantMenuKey = menuKeyMap(tenantId);
        if (tenantId == 1L) {
            // 租户1：本租户角色菜单引用 从平台菜单ID 重指到本租户复制菜单
            Map<String, Long> platformMenuKey = menuKeyMap(null);
            for (SysRole r : templateRoles) remapRoleMenus(r.getId(), platformMenuKey, tenantMenuKey);
        } else if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, tenantId)) == 0) {
            // 其他租户：复制租户1角色（code 对应），菜单引用映射到本租户菜单
            Map<String, Long> t1MenuKey = menuKeyMap(1L);
            Map<String, Long> codeToNewRole = new HashMap<>();
            for (SysRole r : templateRoles) {
                SysRole copy = new SysRole();
                copy.setTenantId(tenantId);
                copy.setCode(r.getCode());
                copy.setName(r.getName());
                copy.setRemark(r.getRemark());
                roleMapper.insert(copy);
                codeToNewRole.put(r.getCode(), copy.getId());
                for (SysRoleMenu rm : roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, r.getId()))) {
                    Long newMenuId = tenantMenuKey.get(menuKeyOf(t1MenuKey, rm.getMenuId()));
                    if (newMenuId == null) continue;
                    SysRoleMenu nrm = new SysRoleMenu();
                    nrm.setRoleId(copy.getId());
                    nrm.setMenuId(newMenuId);
                    roleMenuMapper.insert(nrm);
                }
            }
            // 该租户既有用户：租户1角色换绑为本租户角色
            for (SysUser u : userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getTenantId, tenantId))) {
                for (SysUserRole ur : userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, u.getId()))) {
                    SysRole bound = roleMapper.selectById(ur.getRoleId());
                    if (bound == null || bound.getTenantId() == null
                            || bound.getTenantId() != 1L) continue;
                    Long newRoleId = codeToNewRole.get(bound.getCode());
                    if (newRoleId != null) {
                        ur.setRoleId(newRoleId);
                        userRoleMapper.updateById(ur);
                    }
                }
            }
        }

        // 4. 租户管理员：每个企业统一的 admin 账号（用户名租户内唯一），初始密码 123456
        SysUser existing = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, "admin"));
        if (existing == null) {
            SysUser admin = new SysUser();
            admin.setTenantId(tenantId);
            admin.setUsername("admin");
            admin.setRealName(tenant.getName() + "管理员");
            admin.setUserType(1);
            admin.setStatus(1);
            admin.setPassword(DigestUtils.md5DigestAsHex(("123456" + AuthService.SALT).getBytes()));
            userMapper.insert(admin);
        }
        tenant.setAdminUsername("admin");
        tenantMapper.updateById(tenant);
    }

    /**
     * 系统字典种子（幂等）：字典值按「类型+值」、字典类型按「类型」比对，
     * 仅补齐模板(租户0)有而本租户缺失的项，不覆盖租户已维护的数据。
     */
    private void seedDicts(Long tenantId) {
        List<SysDict> templates = dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getTenantId, 0L).orderByAsc(SysDict::getDictType, SysDict::getSort));
        if (!templates.isEmpty()) {
            Set<String> owned = new HashSet<>();
            for (SysDict d : dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                    .eq(SysDict::getTenantId, tenantId)))
                owned.add(d.getDictType() + ':' + d.getValue());
            for (SysDict t : templates) {
                if (owned.contains(t.getDictType() + ':' + t.getValue())) continue;
                SysDict copy = new SysDict();
                copy.setTenantId(tenantId);
                copy.setDictType(t.getDictType());
                copy.setLabel(t.getLabel());
                copy.setValue(t.getValue());
                copy.setSort(t.getSort());
                dictMapper.insert(copy);
            }
        }
        for (SysDictType t : dictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, 0L))) {
            if (dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                    .eq(SysDictType::getTenantId, tenantId)
                    .eq(SysDictType::getDictType, t.getDictType())) > 0) continue;
            SysDictType copy = new SysDictType();
            copy.setTenantId(tenantId);
            copy.setDictType(t.getDictType());
            copy.setName(t.getName());
            dictTypeMapper.insert(copy);
        }
    }

    /** 租户菜单键表：type:path -> id（tenantId=0 表示平台模板） */
    private Map<String, Long> menuKeyMap(Long tenantId) {
        LambdaQueryWrapper<SysMenu> qw = new LambdaQueryWrapper<>();
        qw.eq(SysMenu::getTenantId, tenantId == null ? 0L : tenantId);
        Map<String, Long> map = new HashMap<>();
        for (SysMenu m : menuMapper.selectList(qw))
            map.put(menuKey(m), m.getId());
        return map;
    }

    private String menuKey(SysMenu m) {
        return m.getType() + ":" + (m.getPath() == null ? "" : m.getPath());
    }

    /** 在键表中查菜单ID对应的键（查不到返回不可能命中的键） */
    private String menuKeyOf(Map<String, Long> keyMap, Long menuId) {
        for (Map.Entry<String, Long> e : keyMap.entrySet())
            if (e.getValue().equals(menuId)) return e.getKey();
        return "#none";
    }

    /** 角色菜单引用重映射（按菜单键从源键表指向目标键表），幂等 */
    private void remapRoleMenus(Long roleId, Map<String, Long> sourceKeyMap, Map<String, Long> targetKeyMap) {
        for (SysRoleMenu rm : roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                .eq(SysRoleMenu::getRoleId, roleId))) {
            Long newId = targetKeyMap.get(menuKeyOf(sourceKeyMap, rm.getMenuId()));
            if (newId != null && !newId.equals(rm.getMenuId())) {
                rm.setMenuId(newId);
                roleMenuMapper.updateById(rm);
            }
        }
    }

    /**
     * 删除租户（软删除，可恢复）：仅停用该租户全部用户，用户/角色/菜单/组织/业务数据全部保留；
     * 误删可在回收站一键恢复（restore 重建启用状态，缺失的默认数据由 provision 幂等补齐）。
     */
    @Transactional
    public void purge(Long tenantId) {
        // 停用该租户全部用户（登录与访问立即被拒），不物理删除任何数据
        SysUser disable = new SysUser();
        disable.setStatus(0);
        userMapper.update(disable, new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId));
    }

    /**
     * 恢复租户：取消软删 + 启用用户；若菜单/角色/管理员被旧逻辑物理清理过，
     * provision 会幂等重建（已有则跳过复制），实现完整还原。
     */
    @Transactional
    public void restore(Long tenantId) {
        SysTenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null) {
            // 软删中的行 selectById 查不到（@TableLogic），用原生恢复后再加载
            tenantMapper.restore(tenantId);
            tenant = tenantMapper.selectById(tenantId);
        }
        if (tenant == null) throw new BizException("租户不存在");
        tenantMapper.restore(tenantId);
        // 重新启用该租户用户（含曾停用的管理员）
        SysUser enable = new SysUser();
        enable.setStatus(1);
        userMapper.update(enable, new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getStatus, 0));
        // 幂等补齐默认数据（菜单/角色/管理员，缺失才建）
        provision(tenantId);
    }

    /** 带 tenant_id 的业务/系统表（物理清除范围）；sys_log 审计日志永不删除 */
    private static final String[] PURGE_TABLES = {
            "sys_notice", "sys_notify_config", "sys_org", "sys_menu", "sys_role",
            "sys_dict", "sys_dict_type",
            "edu_schedule_log", "edu_schedule", "edu_absence", "edu_leave",
            "edu_consume", "edu_enroll", "edu_evaluation", "edu_payment_log", "edu_payment",
            "edu_refund", "edu_contract", "edu_clazz", "edu_classroom", "edu_course",
            "edu_package", "edu_student_log", "edu_student", "edu_teacher"
    };

    /**
     * 物理删除企业数据（不可恢复，仅回收站中的企业可执行）：
     * 清除该企业 用户/角色/菜单/组织/通知/全部业务表 的所有行并保留审计记录。
     */
    @Transactional
    public String physicalPurge(Long tenantId, String operator) {
        // 仅回收站中的企业可物理清除（先软删入站，再物理清除）
        SysTenant tenant = tenantMapper.deletedList().stream()
                .filter(t -> tenantId.equals(t.getId())).findFirst().orElse(null);
        if (tenant == null) throw new BizException("仅回收站中的企业可物理删除");

        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        // 1) 关联表先清（依赖主表ID）
        counts.put("sys_user_role", jdbcTemplate.update(
                "DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE tenant_id = ?)", tenantId));
        counts.put("sys_role_menu", jdbcTemplate.update(
                "DELETE FROM sys_role_menu WHERE role_id IN (SELECT id FROM sys_role WHERE tenant_id = ?)", tenantId));
        counts.put("edu_consult_msg", jdbcTemplate.update(
                "DELETE FROM edu_consult_msg WHERE consult_id IN (SELECT id FROM edu_consult WHERE tenant_id = ?)", tenantId));
        // 2) 主表与业务表
        for (String table : PURGE_TABLES) {
            counts.put(table, jdbcTemplate.update("DELETE FROM " + table + " WHERE tenant_id = ?", tenantId));
        }
        counts.put("edu_consult", jdbcTemplate.update("DELETE FROM edu_consult WHERE tenant_id = ?", tenantId));
        counts.put("sys_user", jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ?", tenantId));
        // 3) 租户行物理删除
        jdbcTemplate.update("DELETE FROM sys_tenant WHERE id = ?", tenantId);

        // 4) 审计留痕：谁、什么时间、清了哪个企业、各表行数（sys_log 操作日志另行保留）
        StringBuilder detail = new StringBuilder();
        counts.forEach((t, n) -> detail.append(t).append('=').append(n).append(' '));
        SysPurgeLog log = new SysPurgeLog();
        log.setTenantId(tenantId);
        log.setTenantName(tenant.getName());
        log.setOperator(operator);
        log.setOperation("物理删除企业数据");
        log.setDetail(detail.toString().trim());
        purgeLogMapper.insert(log);
        return log.getDetail();
    }
}
