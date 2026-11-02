# 单租户后台管理系统 → 行级多租户改造方案（SpringBoot3 + MyBatis-Plus + JWT）

> 适用模块：组织管理、用户管理、角色管理、菜单管理、操作日志管理及各业务模块。
> 方案：共享库共享表，行级 `tenant_id` 隔离；MyBatis-Plus 拦截器自动填充 + 自动拼接查询条件；
> 平台管理员（`tenant_id=0`）全局可管理，租户管理员仅限本租户。

---

## ① 整体改造方案说明

### 1.1 目标架构

```
                    ┌────────────────────────────────┐
                    │  平台管理员 tenant_id = 0        │
                    │  租户CRUD / 套餐 / 启停 / 过期    │
                    └───────────────┬────────────────┘
                                    │ 创建租户时初始化
        ┌───────────────────────────┼───────────────────────────┐
        │ 租户A (tenant_id=1001)     │      租户B (tenant_id=1002) │
        │ 用户/角色/菜单/组织/日志/业务 │      （各自完全隔离）        │
        └───────────────────────────┴───────────────────────────┘
共享库共享表：每张隔离表带 tenant_id 行级隔离，拦截器自动注入
```

### 1.2 改造顺序（强烈建议按此顺序，降低返工）

1. **表结构**：先加 `tenant_id` 列（`DEFAULT 0`）+ 索引，不动业务代码 —— 存量数据全部归平台租户，系统照常运行；
2. **基础设施**：`TenantContext` → `MetaObjectHandler`（插入填充）→ `TenantLineInnerInterceptor`（查询改写）→ JWT 载荷扩展 → 认证过滤器写入上下文；
3. **白名单调通**：登录接口、租户表本身等加入忽略清单，保证系统可登录可管理；
4. **租户模块**：`tenant` 表 + 租户 CRUD + 初始化逻辑；
5. **存量模块摘除手工隔离**：删除各 Controller/Service 里手写的 `tenant_id` 条件（由拦截器接管），保留**归属校验**（越权防护）；
6. **日志与审计**：操作日志自动记录 `tenant_id`；
7. **存量数据分配**：按需把数据批量迁移到目标租户，见 ⑩。

### 1.3 风险点与注意事项

| 风险 | 说明 | 对策 |
|---|---|---|
| 查询改写遗漏 | 手写 XML/Wrapper 的 SQL 若走非 MP 通道不会被改写 | 统一走 MP；原生 SQL 的 XML 加 `@InterceptorIgnore` 并手写条件；上线前用 SQL 日志核对 |
| 平台管理员穿透 | `tenant_id=0` 忽略条件，若误配置会把平台查询带进租户写 | 写操作仍强制归属校验；平台账号单独审计日志 |
| 唯一约束冲突 | 原 `username`/`role_name` 全局唯一，多租户后不同租户需要同名 | 唯一索引改为 `(tenant_id, username)`、`(tenant_id, name)` |
| JWT 里的租户信息过期 | 租户被禁用/过期后旧 token 仍在有效期 | 认证过滤器**每次请求**校验租户状态（本地缓存 60s） |
| 越权（ID 直捣） | 拦截器已把 `getById` 加上租户条件，租户查不到他租户数据 | 但**平台管理员忽略条件**，凡平台执行的写接口必须二次校验目标租户 |
| 菜单初始化膨胀 | 每租户复制整套菜单导致数据量翻倍 | 菜单分两类：平台全局菜单（`tenant_id=0` 只读共享）+ 租户私有菜单（仅需要定制时复制） |
| 分页 count | `TenantLineInnerInterceptor` 会同时改写 count，注意拦截器注册顺序 | TenantLine 必须注册在 Pagination 之前 |

---

## ② 数据库 DDL 脚本

```sql
-- =====================================================================
-- 多租户改造 DDL（MySQL 8）
-- 约定：tenant_id = 0 表示平台（全局数据）；软删除 deleted=1
-- =====================================================================

-- 1. 租户表（本身不带 tenant_id，属于平台级表）
CREATE TABLE `tenant` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '租户ID（平台固定为0，业务租户从1001自增）',
  `tenant_name`    VARCHAR(64)  NOT NULL COMMENT '租户名称',
  `contact_person` VARCHAR(32)  NULL COMMENT '联系人',
  `contact_phone`  VARCHAR(20)  NULL COMMENT '联系电话',
  `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `expire_time`    DATETIME     NULL COMMENT '过期时间（NULL=永久）',
  `package_code`   VARCHAR(32)  NULL COMMENT '套餐编码（关联租户套餐）',
  `remark`         VARCHAR(255) NULL COMMENT '备注',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除：0-正常 1-已删除',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by`      VARCHAR(64)  NULL COMMENT '创建人',
  `update_by`      VARCHAR(64)  NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tenant_name` (`tenant_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户表';

-- 平台租户固定记录（id=0 手工插入，不自增）
INSERT INTO `tenant` (`id`, `tenant_name`, `status`, `remark`)
VALUES (0, '平台', 1, '平台租户，tenant_id=0 保留值');

-- 2. 原有隔离表增加 tenant_id（存量数据默认归平台 0，后续按 ⑩ 迁移）
-- 用户表：唯一约束改为 租户内唯一
ALTER TABLE `sys_user`
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_user_tenant` (`tenant_id`),
  DROP INDEX `uk_username`, ADD UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`);

-- 角色表：不同租户可存在同名角色
ALTER TABLE `sys_role`
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_role_tenant` (`tenant_id`),
  DROP INDEX `uk_role_name`, ADD UNIQUE KEY `uk_tenant_role_name` (`tenant_id`, `name`);

-- 组织表
ALTER TABLE `sys_org`
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_org_tenant` (`tenant_id`);

-- 菜单表：tenant_id=0 为平台全局菜单（共享只读）；租户私有菜单归属对应租户
ALTER TABLE `sys_menu`
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台全局菜单）' AFTER `id`,
  ADD COLUMN `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '是否租户初始化默认菜单模板：0-否 1-是' AFTER `visible`,
  ADD INDEX `idx_menu_tenant` (`tenant_id`);

-- 操作日志表：审计溯源需要按租户隔离
ALTER TABLE `operation_log`
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID（操作人所属租户）' AFTER `id`,
  ADD INDEX `idx_log_tenant` (`tenant_id`);

-- 3. 业务表示例（所有业务表同样处理）
ALTER TABLE `biz_order`
  ADD COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID' AFTER `id`,
  ADD INDEX `idx_order_tenant` (`tenant_id`);
-- ... 其余业务表按同样模式追加
```

### application.yml 配置示例

```yaml
mybatis-plus:
  global-config:
    db-config:
      logic-delete-field: deleted      # 全局软删除字段
      logic-delete-value: 1
      logic-not-delete-value: 0
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl  # 改造期核对SQL改写，上线移除

# 自定义租户配置（忽略清单：无 tenant_id 的平台级表）
tenant:
  ignore-tables:
    - tenant            # 租户表本身
    - sys_config        # 全局系统配置
    - sys_dict          # 全局字典
    - tenant_package    # 租户套餐表（平台维护）
  # 登录等匿名接口白名单（认证过滤器里放行，拦截器此时无上下文自动跳过）
  anon-urls:
    - /auth/login
    - /auth/captcha

jwt:
  secret: "ChangeMe-32-chars-minimum-secret!!"
  expire-minutes: 720
```

---

## ③ 租户实体类 Tenant.java

```java
package com.example.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户实体
 * 注意：tenant 表属于平台级表，不参与租户行级隔离（Mapper 上 @InterceptorIgnore）
 */
@Data
@TableName("tenant")
public class Tenant {

    /** 租户ID：0=平台保留值，业务租户从1001起 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户名称（全局唯一） */
    private String tenantName;

    private String contactPerson;

    private String contactPhone;

    /** 0-禁用 1-启用（禁用后租户内所有用户无法登录） */
    private Integer status;

    /** 过期时间：NULL=永久有效 */
    private LocalDateTime expireTime;

    /** 套餐编码 */
    private String packageCode;

    private String remark;

    /** 软删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 便捷判断：租户是否可用（启用且未过期） */
    public boolean usable() {
        return status != null && status == 1
                && (expireTime == null || expireTime.isAfter(LocalDateTime.now()));
    }
}
```

---

## ④ 租户上下文 TenantContext

```java
package com.example.common.tenant;

/**
 * 租户上下文（ThreadLocal）：认证过滤器写入，请求结束清理。
 * 全链路（拦截器/Service/AOP日志）统一从这里取当前身份，禁止信任前端传参。
 */
public final class TenantContext {

    private static final ThreadLocal<LoginInfo> HOLDER = new ThreadLocal<>();

    /** 请求级登录信息 */
    public record LoginInfo(Long userId, Long tenantId, boolean platformAdmin, String username) {
        /** 平台租户固定 0 */
        public boolean platform() { return tenantId != null && tenantId == 0L; }
    }

    private TenantContext() {}

    public static void set(Long userId, Long tenantId, boolean platformAdmin, String username) {
        HOLDER.set(new LoginInfo(userId, tenantId, platformAdmin, username));
    }

    /** 平台管理员切换目标租户视角（仅平台管理员可用，见 ⑨-1） */
    public static void swapTenant(Long targetTenantId) {
        LoginInfo cur = HOLDER.get();
        if (cur == null) {
            throw new IllegalStateException("未登录上下文，禁止切换租户");
        }
        if (!cur.platformAdmin()) {
            throw new SecurityException("仅平台管理员可切换租户视角");
        }
        HOLDER.set(new LoginInfo(cur.userId(), targetTenantId, false, cur.username()));
    }

    public static Long getTenantId() {
        LoginInfo info = HOLDER.get();
        return info == null ? null : info.tenantId();
    }

    public static Long getUserId() {
        LoginInfo info = HOLDER.get();
        return info == null ? null : info.userId();
    }

    public static String getUsername() {
        LoginInfo info = HOLDER.get();
        return info == null ? null : info.username();
    }

    /** 是否平台管理员（tenant_id=0 的管理身份） */
    public static boolean isPlatformAdmin() {
        LoginInfo info = HOLDER.get();
        return info != null && info.platformAdmin();
    }

    /** 是否已登录（未登录的匿名请求，如登录接口，查询不做租户改写） */
    public static boolean loggedIn() {
        return HOLDER.get() != null;
    }

    /** 必须在 finally 中清理，防止线程复用串租户（重大安全点） */
    public static void clear() {
        HOLDER.remove();
    }
}
```

---

## ⑤ MyBatis 拦截器（自动填充 + 查询条件自动拼接）

### 5.1 插入自动填充：tenant_id / 时间 / 操作人

```java
package com.example.common.tenant;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 字段自动填充：
 * 1) INSERT：tenant_id（取登录上下文）、create_time/create_by、update_time/update_by
 * 2) UPDATE：update_time/update_by
 * 平台管理员场景：允许 Service 层在实体上显式 setTenantId(目标租户)，
 * 已有值则不再覆盖（见 strictInsertFill 语义），实现"平台代租户建数据"。
 */
@Component
public class TenantMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // ---- tenant_id：实体未显式指定时才从上下文填充 ----
        Object explicit = getFieldValByName("tenantId", metaObject);
        if (explicit == null) {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                strictInsertFill(metaObject, "tenantId", Long.class, tenantId);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createBy", String.class, TenantContext.getUsername());
        strictInsertFill(metaObject, "updateBy", String.class, TenantContext.getUsername());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updateBy", String.class, TenantContext.getUsername());
    }
}
```

### 5.2 查询条件自动拼接：TenantLineInnerInterceptor

```java
package com.example.common.tenant;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

/**
 * 行级租户隔离核心：
 * - 所有 SQL（含 Wrapper、XML、分页 count）自动追加 WHERE tenant_id = ?
 * - 平台管理员 / 未登录（登录接口）/ 白名单表：跳过改写
 * - TenantLine 必须注册在 Pagination 之前，否则分页 count 不带租户条件
 */
@Configuration
public class TenantMybatisConfig {

    /** 平台级表（无 tenant_id 列）白名单，来自 yml tenant.ignore-tables */
    private final Set<String> ignoreTables;

    public TenantMybatisConfig(@Value("${tenant.ignore-tables}") Set<String> ignoreTables) {
        this.ignoreTables = new HashSet<>(ignoreTables);
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1) 租户行级拦截器（必须在分页之前注册）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler()));
        // 2) 分页拦截器
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
        return interceptor;
    }

    private TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {

            /** 当前注入的租户值：以登录上下文为准（后端可信，前端不可传） */
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.getTenantId();
                if (tenantId == null) {
                    // 理论上 ignoreTable 已挡住未登录场景，这里兜底防注入失败
                    throw new IllegalStateException("租户上下文缺失，拒绝执行");
                }
                return new LongValue(tenantId);
            }

            /**
             * 跳过改写的场景：
             * a) 白名单平台表（tenant / sys_config / 套餐表等）
             * b) 平台管理员：需要看全部租户数据
             * c) 未登录：登录接口按用户名全局查用户
             */
            @Override
            public boolean ignoreTable(String tableName) {
                String pure = tableName.replace("`", "").toLowerCase();
                if (ignoreTables.contains(pure)) return true;
                if (!TenantContext.loggedIn()) return true;
                return TenantContext.isPlatformAdmin();
            }

            /** 平台管理员手动代租户写数据：INSERT 已显式带 tenant_id 时不重复追加 */
            @Override
            public boolean ignoreInsert(String columns, String tenantIdColumn) {
                return columns.toUpperCase().contains(tenantIdColumn.toUpperCase());
            }
        };
    }
}
```

### 5.3 免隔离注解（平台级 Mapper 使用）

```java
package com.example.common.tenant;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;

/**
 * 平台级 Mapper 基类：tenant 表、套餐表等自身不参与行级隔离。
 * （@InterceptorIgnore 对该 Mapper 的所有方法关闭 tenantLine 改写）
 */
@InterceptorIgnore(tenantLine = "true")
public interface PlatformMapper<T> extends com.baomidou.mybatisplus.core.mapper.BaseMapper<T> {
}
```

---

## ⑥ 登录认证改造（JWT 载荷 + 过滤器）

### 6.1 JWT 载荷与工具类

```java
package com.example.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * JWT：载荷携带 userId / tenantId / isPlatformAdmin
 * 认证过滤器解析后写入 TenantContext；租户状态每次请求实时校验（见 6.2）
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-minutes}")
    private long expireMinutes;

    public String createToken(Long userId, Long tenantId, boolean platformAdmin) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .addClaims(Map.of(
                        "tenantId", tenantId,
                        "isPlatformAdmin", platformAdmin
                ))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMinutes * 60_000))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secret)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
```

### 6.2 认证过滤器：写入租户上下文 + 租户状态实时校验

```java
package com.example.common.security;

import com.example.common.tenant.TenantContext;
import com.example.system.entity.Tenant;
import com.example.system.mapper.TenantMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 认证与租户上下文过滤器：
 * 1) 解析 JWT → 写入 TenantContext（userId/tenantId/isPlatformAdmin）
 * 2) 每次请求实时校验租户状态（禁用/过期立即踢下线，防旧 token 残留）
 * 3) 平台管理员支持 X-Target-Tenant 请求头切换目标租户视角（⑨-1）
 * 4) finally 清理 ThreadLocal（线程池复用防串租户）
 */
@Component
@RequiredArgsConstructor
public class AuthTenantFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TenantMapper tenantMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp,
                                    FilterChain chain) throws ServletException, IOException {
        String token = req.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            try {
                var claims = jwtUtil.parse(token.substring(7));
                Long userId = Long.valueOf(claims.getSubject());
                Long tenantId = claims.get("tenantId", Long.class);
                boolean platformAdmin = Boolean.TRUE.equals(claims.get("isPlatformAdmin", Boolean.class));

                // ---- 租户状态实时校验（平台租户 0 跳过）----
                if (tenantId != 0) {
                    Tenant tenant = tenantMapper.selectById(tenantId);
                    if (tenant == null || !tenant.usable()) {   // 禁用或过期
                        resp.setStatus(401);
                        resp.setContentType("application/json;charset=UTF-8");
                        resp.getWriter().write("{\"code\":401,\"msg\":\"租户已停用或过期，请联系平台\"}");
                        return;
                    }
                }

                TenantContext.set(userId, tenantId, platformAdmin, claims.get("username", String.class));

                // ---- 平台管理员切换目标租户视角（仅平台身份允许）----
                String target = req.getHeader("X-Target-Tenant");
                if (target != null && platformAdmin) {
                    TenantContext.swapTenant(Long.valueOf(target));
                }
            } catch (Exception e) {
                // token 无效：放行到授权层统一 401，或直接在此响应
                resp.setStatus(401);
                resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().write("{\"code\":401,\"msg\":\"登录已失效\"}");
                return;
            }
        }
        try {
            chain.doFilter(req, resp);
        } finally {
            TenantContext.clear();   // 关键：线程复用必须清理
        }
    }
}
```

### 6.3 登录接口（租户状态校验 + 签发带租户信息的 token）

```java
@PostMapping("/login")
public Result<LoginVO> login(@RequestBody LoginDTO dto) {
    SysUser user = authService.verifyCredential(dto);   // 按用户名全局查（唯一索引 (tenant_id, username) + 前缀查询）
    // ---- 登录即校验租户可用性（禁用/过期禁止登录，⑨-3）----
    Tenant tenant = user.getTenantId() == 0 ? null : tenantMapper.selectById(user.getTenantId());
    if (tenant != null && !tenant.usable()) {
        return Result.error("租户已停用或已过期，禁止登录");
    }
    boolean platformAdmin = user.getTenantId() == 0 && "admin".equals(user.getUsername());
    String token = jwtUtil.createToken(user.getId(), user.getTenantId(), platformAdmin);
    return Result.ok(new LoginVO(token, user.getTenantId(), platformAdmin));
}
```

---

## ⑦ 租户模块（Controller / Service / Mapper）

### 7.1 Mapper 与 DTO

```java
package com.example.system.mapper;

import com.example.common.tenant.PlatformMapper;
import com.example.system.entity.Tenant;
import org.apache.ibatis.annotations.Mapper;

/** 租户表为平台级表：继承 PlatformMapper 关闭租户改写 */
@Mapper
public interface TenantMapper extends PlatformMapper<Tenant> {
}
```

```java
package com.example.system.service;

import com.example.system.entity.Tenant;
import lombok.Data;

public class TenantDTO {

    @Data
    public static class Save {
        private Long id;                 // 编辑时传
        private String tenantName;       // 必填
        private String contactPerson;
        private String contactPhone;
        private LocalDateTime expireTime;
        private String packageCode;
        private String remark;
        private Integer status;          // 编辑时启用/禁用
    }

    @Data
    public static class Detail extends Save {
        private Long tenantId;
        private String adminUsername;    // 初始化生成的租户管理员账号
    }
}
```

### 7.2 Service（含创建即初始化，⑨-2）

```java
package com.example.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.common.exception.BizException;
import com.example.common.tenant.TenantContext;
import com.example.system.entity.*;
import com.example.system.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 租户管理（仅平台管理员可用，Controller 层 @PlatformOnly 拦截）
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;
    private final SysMenuMapper menuMapper;          // 继承 PlatformMapper：菜单模板 tenant_id=0 全局共享
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserMapper userMapper;

    /** 分页列表（平台视角，无租户过滤） */
    public PageResult<Tenant> page(long page, long size, String keyword, Integer status) {
        var p = tenantMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Tenant>()
                        .like(StringUtils.hasText(keyword), Tenant::getTenantName, keyword)
                        .eq(status != null, Tenant::getStatus, status)
                        .orderByDesc(Tenant::getId));
        return PageResult.of(p.getTotal(), p.getRecords());
    }

    /** 创建租户：租户记录 + 默认菜单 + 默认角色 + 管理员账号，同一事务（⑨-2） */
    @Transactional(rollbackFor = Exception.class)
    public Tenant create(TenantDTO.Save dto) {
        if (!StringUtils.hasText(dto.getTenantName())) throw new BizException("租户名称不能为空");
        Long dup = tenantMapper.selectCount(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getTenantName, dto.getTenantName()));
        if (dup > 0) throw new BizException("租户名称已存在");

        // 1) 租户主记录（tenant 表无 tenant_id，手动填平台公共字段）
        Tenant tenant = new Tenant();
        tenant.setTenantName(dto.getTenantName());
        tenant.setContactPerson(dto.getContactPerson());
        tenant.setContactPhone(dto.getContactPhone());
        tenant.setExpireTime(dto.getExpireTime());
        tenant.setPackageCode(dto.getPackageCode());
        tenant.setRemark(dto.getRemark());
        tenant.setStatus(1);
        tenant.setCreateBy(TenantContext.getUsername());
        tenantMapper.insert(tenant);

        // 2) 初始化：默认角色（复制租户菜单模板）+ 租户管理员
        initTenantDefaults(tenant);

        return tenant;
    }

    /**
     * 租户默认数据初始化（幂等：已有默认角色则跳过）：
     * - 默认菜单：菜单表中 is_default=1 的平台菜单（tenant_id=0）直接**共享引用**，不复制，
     *   租户需要定制时再在租户菜单里新增/覆盖（控制数据膨胀）；
     * - 默认角色"租户管理员"：分配全部默认菜单；
     * - 默认管理员账号：tenant_code（初始密码 123456），归属本租户。
     */
    private void initTenantDefaults(Tenant tenant) {
        // 平台管理员创建租户数据：实体显式 setTenantId（MetaObjectHandler 不覆盖）
        SysRole adminRole = new SysRole();
        adminRole.setTenantId(tenant.getId());          // 关键：显式指定目标租户
        adminRole.setName("租户管理员");
        adminRole.setCode("TENANT_ADMIN_" + tenant.getId());
        roleMapper.insert(adminRole);

        // 分配全部默认菜单（is_default=1 的平台全局菜单）
        List<Long> defaultMenuIds = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                        .eq(SysMenu::getIsDefault, 1).select(SysMenu::getId))
                .stream().map(SysMenu::getId).toList();
        for (Long menuId : defaultMenuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(adminRole.getId());
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }

        // 租户管理员账号（用户名全局唯一约束按 (tenant_id, username)）
        SysUser admin = new SysUser();
        admin.setTenantId(tenant.getId());              // 显式指定目标租户
        admin.setUsername(generateAdminUsername(tenant));
        admin.setRealName(tenant.getTenantName() + "管理员");
        admin.setUserType(2);                           // 2=租户管理员（1=平台管理员，仅平台使用）
        admin.setStatus(1);
        admin.setPassword(DigestUtils.md5DigestAsHex("123456".getBytes()));
        userMapper.insert(admin);
    }

    private String generateAdminUsername(Tenant tenant) {
        // 拼音编码或 "t" + tenantId，保证 (tenant_id, username) 内唯一且好记
        return "admin" + tenant.getId();
    }

    /** 编辑（名称/联系人/套餐/备注/过期时间） */
    @Transactional(rollbackFor = Exception.class)
    public void update(TenantDTO.Save dto) {
        Tenant db = tenantMapper.selectById(dto.getId());
        if (db == null) throw new BizException("租户不存在");
        db.setTenantName(dto.getTenantName());
        db.setContactPerson(dto.getContactPerson());
        db.setContactPhone(dto.getContactPhone());
        db.setExpireTime(dto.getExpireTime());
        db.setPackageCode(dto.getPackageCode());
        db.setRemark(dto.getRemark());
        db.setUpdateBy(TenantContext.getUsername());
        tenantMapper.updateById(db);
    }

    /**
     * 启用/禁用（⑨-3）：禁用后认证过滤器实时拦截租户内所有用户（旧 token 也立即失效）
     */
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long tenantId, boolean enable) {
        Tenant db = tenantMapper.selectById(tenantId);
        if (db == null) throw new BizException("租户不存在");
        db.setStatus(enable ? 1 : 0);
        db.setUpdateBy(TenantContext.getUsername());
        tenantMapper.updateById(db);
    }

    /** 详情 */
    public Tenant detail(Long id) {
        Tenant db = tenantMapper.selectById(id);
        if (db == null) throw new BizException("租户不存在");
        return db;
    }

    /** 软删除：置 deleted=1（统一软删除，禁止物理删除） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == 0L) throw new BizException("平台租户不允许删除");
        Tenant db = tenantMapper.selectById(id);
        if (db == null) throw new BizException("租户不存在");
        tenantMapper.deleteById(id);   // @TableLogic 自动转为 UPDATE deleted=1
    }
}
```

### 7.3 Controller（平台管理员专属）

```java
package com.example.system.controller;

/**
 * 租户管理：仅平台管理员（tenant_id=0 且平台管理身份）可访问。
 * 实现方式：自定义 @PlatformOnly 注解 + 拦截器统一鉴权，业务代码零侵入。
 */
@RestController
@RequestMapping("/tenant")
@RequiredArgsConstructor
@PlatformOnly
public class TenantController {

    private final TenantService tenantService;

    @GetMapping("/page")
    public Result<PageResult<Tenant>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Integer status) {
        return Result.ok(tenantService.page(page, size, keyword, status));
    }

    @PostMapping
    @Log("新增租户")
    public Result<Tenant> create(@RequestBody TenantDTO.Save dto) {
        return Result.ok(tenantService.create(dto));
    }

    @PutMapping
    @Log("修改租户")
    public Result<Void> update(@RequestBody TenantDTO.Save dto) {
        tenantService.update(dto);
        return Result.ok();
    }

    /** 启用/禁用（禁用后该租户所有用户立即无法访问） */
    @PostMapping("/{id}/toggle")
    @Log("启用/禁用租户")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enable) {
        tenantService.toggle(id, enable);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<Tenant> detail(@PathVariable Long id) {
        return Result.ok(tenantService.detail(id));
    }

    @DeleteMapping("/{id}")
    @Log("删除租户")
    public Result<Void> delete(@PathVariable Long id) {
        tenantService.delete(id);
        return Result.ok();
    }
}
```

```java
package com.example.common.security;

import java.lang.annotation.*;

/** 仅平台管理员可访问（HandlerInterceptor 统一校验 TenantContext.isPlatformAdmin()） */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PlatformOnly {
}
```

```java
package com.example.common.security;

import com.example.common.exception.BizException;
import com.example.common.tenant.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** @PlatformOnly 注解拦截：非平台管理员直接 403，防止租户管理员越权调平台接口 */
@Component
public class PlatformOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(jakarta.servlet.http.HttpServletRequest req,
                             jakarta.servlet.http.HttpServletResponse resp, Object handler) {
        if (!(handler instanceof HandlerMethod hm)) return true;
        PlatformOnly anno = hm.getBeanType().getAnnotation(PlatformOnly.class);
        if (anno == null) anno = hm.getMethodAnnotation(PlatformOnly.class);
        if (anno != null && !TenantContext.isPlatformAdmin()) {
            throw new BizException("403");
        }
        return true;
    }
}
```

---

## ⑧ 原有模块改造说明

> 核心原则：**查询隔离交给拦截器，写操作归属校验保留在业务层**；删除所有手写的
> `eq(tenant_id)` 查询条件，避免双重条件与遗漏两种 bug 并存。

### 8.1 用户管理 `SysUserController / SysUserService`
- **删除**：列表里手动的租户过滤、`save` 里手动 `setTenantId`（改由 `MetaObjectHandler` 自动填充）；
- **保留/新增**：
  - 更新/删除/重置密码前校验目标用户可触达（`selectById` 已被拦截器加租户条件，查不到即越权，直接 404 处理）；
  - **超级管理员保护（⑨-4）**：目标 `tenant_id=0` 的用户，仅平台管理员可改/可删；`admin` 账号禁止删除与禁用；
  - 用户名唯一性改为 `(tenant_id, username)` 联合唯一，注册查重 SQL 需带当前租户（自动）。
- 平台管理员代建租户用户：实体显式 `setTenantId(目标租户)`，填充器不覆盖。

### 8.2 角色管理 `SysRoleController`
- 列表/新增自动隔离；**角色名唯一改为租户内唯一** `(tenant_id, name)`；
- "分配菜单"接口：菜单树查询已被拦截器过滤（租户只见 平台全局菜单 tenant_id=0 ∪ 本租户菜单），
  直接取前端 menuIds 保存即可；**平台管理员给租户角色配菜单时注意只能选该租户可见集**。

### 8.3 组织管理 `SysOrgController`
- 树查询 `selectList` 自动追加租户条件，树构建逻辑不变；
- 新增组织自动归属当前租户；parentId 必须是本租户组织（查得到即合法，天然校验）。

### 8.4 菜单管理 `SysMenuController`
- 平台全局菜单（`tenant_id=0`）：平台管理员维护，租户**只读共享**（查询条件=0 或本租户，
  见下方联合查询）；
- 租户私有菜单：租户管理员 CRUD，自动归属；
- 角色菜单树查询需要"平台菜单 ∪ 本租户菜单"，用 `@InterceptorIgnore` 的原生查询或
  平台管理员等价逻辑单独实现：

```java
/** 租户可见菜单 = 平台全局菜单(tenant_id=0) + 本租户私有菜单（免拦截器，手写联合条件） */
@InterceptorIgnore(tenantLine = "true")
@Select("SELECT * FROM sys_menu WHERE deleted = 0 AND (tenant_id = 0 OR tenant_id = #{tenantId}) ORDER BY sort")
List<SysMenu> selectVisibleMenus(@Param("tenantId") Long tenantId);
```

### 8.5 操作日志 `OperationLog / LogAspect`
- 日志表已加 `tenant_id`，`LogAspect` 落库时由 `MetaObjectHandler` 自动注入当前操作人租户；
- AOP 取操作人统一改从 `TenantContext` 读取（不再从请求参数解析）：

```java
sysLog.setTenantId(TenantContext.getTenantId());   // 显式冗余一份亦可，审计表建议显式
sysLog.setUserId(TenantContext.getUserId());
```

### 8.6 通用越权校验模板（写操作）

```java
/** 写操作归属校验：拦截器挡住了"查"，这里挡"改"（尤其平台管理员无过滤时） */
private SysUser ownedUser(Long id) {
    SysUser target = userMapper.selectById(id);   // 租户上下文下查不到=越权/不存在
    if (target == null) throw new BizException("用户不存在或无权操作");
    // 平台管理员忽略租户过滤时补一道显式防线
    if (!TenantContext.isPlatformAdmin()
            && !TenantContext.getTenantId().equals(target.getTenantId())) {
        throw new BizException("不能操作其他租户的数据");
    }
    return target;
}
```

---

## ⑨ 重要边界 Case 处理

### 9.1 平台管理员如何查看全部租户数据
- **全量视角**：`TenantLineHandler.ignoreTable` 对平台管理员返回 true → 不追加租户条件，天然全量；
- **定向视角**（平台管理员"进入"某租户排障）：请求头 `X-Target-Tenant: 1001`，
  过滤器校验平台身份后 `TenantContext.swapTenant(1001)`，后续读写都按租户 1001 隔离执行；
  租户用户传该头**无效且忽略**（仅 `platformAdmin=true` 分支处理），防篡改。

### 9.2 新建租户初始化（默认角色/菜单/管理员）
见 `TenantService.create → initTenantDefaults`：
- 默认菜单采用**共享引用**（`is_default=1` 的平台菜单），避免每租户复制导致的维护分裂与数据膨胀；
- 默认角色"租户管理员"一次分配全部默认菜单，租户后续可自建角色收敛权限；
- 默认管理员 `admin{tenantId}` / `123456`，强制归属新租户；
- 整个初始化与租户建在同一事务，失败即回滚不留半租户；重试幂等（已有默认角色跳过）。

### 9.3 租户过期/禁用后禁止其所有用户登录
- **登录口**：login 校验 `tenant.usable()`（启用且未过期）；
- **存量会话**：JWT 无状态无法主动作废 → 认证过滤器**每次请求**实时查租户状态
  （生产建议加 60s 本地缓存或 Redis，读多写少），不可用立即 401"租户已停用或过期"；
- 二次保险：`toggle` 禁用时可同时把该租户用户 token 加入 Redis 黑名单（可选增强）。

### 9.4 超级管理员不能被普通租户修改
- 目标用户 `tenant_id=0`：非平台管理员的任何写操作（改/删/禁用/重置密码）直接拒绝；
- `admin` 账号（平台超级管理员）：即使平台管理员也**禁止删除与禁用**，仅允许改密码/资料；
- 落点：`SysUserService` 的 update/delete/resetPassword 统一走 `ownedUser()` + 上述两条硬校验。

---

## ⑩ 单元测试重点与上线迁移方案

### 10.1 单元测试重点（按优先级）

| # | 用例 | 断言要点 |
|---|---|---|
| 1 | 租户A登录查询用户/角色/组织列表 | 仅返回租户A数据；SQL 日志确认带 `tenant_id=1001` |
| 2 | 租户A按ID读取租户B数据（getById 直捣） | 返回 null / 抛业务异常，绝不返回B数据 |
| 3 | 租户A更新/删除租户B数据 | 拒绝（更新影响行数=0 视为不存在） |
| 4 | 平台管理员查询 | 不带租户条件，返回全量 |
| 5 | 平台管理员 + X-Target-Tenant=1002 | 只见 1002 数据 |
| 6 | 租户用户伪造 X-Target-Tenant | 头被忽略，仍按自身租户隔离 |
| 7 | INSERT 自动填充 | tenant_id=上下文租户；平台代建时保留显式值 |
| 8 | 分页 count | count 同样被改写，total 与租户数据一致 |
| 9 | 忽略表 | tenant/sys_config 的 SQL 未追加条件 |
| 10 | 创建租户初始化 | 默认角色/菜单/管理员齐全且归属新租户；事务失败回滚 |
| 11 | 禁用/过期租户 | 登录被拒；存量 token 请求 401 |
| 12 | 租户内同名角色/用户名 | 允许（联合唯一）；跨租户同名互不影响 |
| 13 | ThreadLocal 清理 | 异常请求后再次请求上下文不串租户 |
| 14 | 平台管理员写租户数据 | 目标租户显式校验通过，禁止裸写（防止误写平台0） |

### 10.2 上线迁移方案（存量数据 tenant_id=0 或分配默认租户）

**阶段一：表结构先行（零风险窗口）**
```sql
-- 加列默认0，存量数据自动归属"平台租户"，老逻辑不受影响
ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0, ADD INDEX idx_user_tenant(tenant_id);
-- 其余表同 ②；唯一索引的重建放在低峰期（ALTER 会锁表，建议 gh-ost/pt-osc）
```

**阶段二：存量数据分配**
```sql
-- 方案A：全部留在平台租户（数据量小/演示环境）
-- 方案B：按归属规则批量分配（例如按组织/公司字段）
UPDATE sys_user  SET tenant_id = 1001 WHERE org_id IN (SELECT id FROM sys_org WHERE company_id = 1001);
UPDATE sys_role  SET tenant_id = 1001 WHERE id IN (...);
-- 分配后核验：不允许任何业务表残留 tenant_id IS NULL；平台共享数据（菜单/字典/配置）保持 0
SELECT 'user' t, COUNT(*) FROM sys_user WHERE tenant_id = 0
UNION ALL SELECT 'role', COUNT(*) FROM sys_role WHERE tenant_id = 0;  -- 仅平台自有数据应为0
```

**阶段三：代码灰度上线**
1. 上线含拦截器代码，`tenant.enabled` 配置开关预留（可一键关闭改写做应急回退）；
2. 打开 SQL 日志跑全接口回归（重点：XML 手写 SQL、跨表 join、子查询是否都被正确改写）；
3. 平台管理员全量视角核对各租户数据条数与迁移前一致。

**阶段四：收尾**
- 摘除各模块历史遗留的手工租户过滤（防双条件）；
- 补齐联合唯一索引、清理冗余索引；
- 回滚预案：代码回退上一版本 + `ALTER TABLE ... DROP COLUMN tenant_id`（仅在新方案不可用时），
  因阶段一兼容设计，列保留不影响老代码运行。

---

## 附：与当前 07_edu 工程（SpringBoot 2.7 + Java 8）的落地对照

| 本方案（SB3） | 当前工程对应 | 差异说明 |
|---|---|---|
| TenantLineInnerInterceptor 自动改写 | 各 Controller 手写 scope() 过滤 | 建议迁移到拦截器方案，删除手工过滤 |
| TenantContext(ThreadLocal) | AuthContext | 增加 platformAdmin 与 swapTenant |
| 菜单共享引用(is_default) | 每租户整树复制 | 当前已实施复制方案，可保留；新租户用本方案模板标记更省 |
| JWT 携带 tenantId/isPlatformAdmin | token 仅 userId，每次查库 | 现状更安全（实时），保留亦可 |
| 租户过期时间/套餐 | sys_tenant 无此列 | 可直接 ALTER 补列（expire_time/package_code） |
| 软删除 | sys_menu/sys_role 未软删 | 按需补 deleted 列与 @TableLogic |
```
