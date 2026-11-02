package com.edu.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.entity.SysNotice;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 全局通知查询（绕过租户拦截器）：通知可见性 = 本租户通知 + 平台公告(tenant_id=0)，
     * 租户条件由业务层在 Wrapper 中手工拼装。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_notice ${ew.customSqlSegment}")
    Page<SysNotice> selectGlobalPage(Page<SysNotice> page, @Param(Constants.WRAPPER) Wrapper<SysNotice> wrapper);

    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM sys_notice ${ew.customSqlSegment}")
    List<SysNotice> selectGlobalList(@Param(Constants.WRAPPER) Wrapper<SysNotice> wrapper);
}
