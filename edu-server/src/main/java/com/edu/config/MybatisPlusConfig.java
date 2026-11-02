package com.edu.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * MyBatis-Plus 插件配置：行级租户隔离 + 分页。
 *
 * 租户拦截器（TenantLineInnerInterceptor）自动完成两件事，业务代码不再手写 tenant_id：
 *  1) 查询（含 Wrapper / XML / 分页 count）自动追加 WHERE tenant_id = 当前租户；
 *  2) 插入自动补 tenant_id 列（SQL 已显式带该列则保留显式值——平台管理员代租户建数据）。
 *
 * 跳过改写的场景：
 *  a) 平台级表（无 tenant_id 列）：见 IGNORE_TABLES；
 *  b) 平台管理员（tenant_id=0 的管理身份）：查看全部租户数据；
 *  c) 未登录（登录接口按用户名全局查用户）。
 *
 * 注意：TenantLine 必须注册在 Pagination 之前，否则分页 count 不带租户条件。
 */
@Configuration
@MapperScan("com.edu.mapper")
public class MybatisPlusConfig {

    /** 无 tenant_id 列的平台级表 / 纯关联表：不做租户改写 */
    private static final Set<String> IGNORE_TABLES = new HashSet<>(Arrays.asList(
            "sys_tenant",        // 租户表本身（平台维护）
            "sys_config",        // 全局系统配置（登录页等公共配置）
            "sys_role_menu",     // 角色-菜单关联（随角色隔离）
            "sys_user_role",     // 用户-角色关联（随用户隔离）
            "edu_consult_msg",   // 咨询消息流（随主表咨询隔离）
            "sys_notice_read"    // 通知阅读状态（按用户，无租户列）
    ));

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1) 租户行级拦截器（必须在分页之前）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler()));
        // 2) 分页拦截器
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    private TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {

            /** 注入的租户值：只信登录上下文（后端可信），前端传参无效 */
            @Override
            public Expression getTenantId() {
                LoginUser lu = AuthContext.get();
                Long tenantId = lu == null || lu.getTenantId() == null ? 0L : lu.getTenantId();
                return new LongValue(tenantId);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                String pure = tableName.replace("`", "").toLowerCase();
                if (IGNORE_TABLES.contains(pure)) return true;
                LoginUser lu = AuthContext.get();
                if (lu == null) return true;                    // 未登录（登录接口等）
                return lu.isPlatformAdmin();                    // 平台管理员看全部
            }
            // ignoreInsert 使用 MP 默认实现：INSERT 已显式含 tenant_id 列时保留显式值
        };
    }
}
