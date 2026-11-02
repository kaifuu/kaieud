package com.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.entity.SysTenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SysTenantMapper extends BaseMapper<SysTenant> {

    /** 恢复软删租户（原生SQL绕开 @TableLogic 的 deleted=0 过滤） */
    @Update("UPDATE sys_tenant SET deleted = 0, status = 1 WHERE id = #{id}")
    int restore(Long id);

    /** 回收站：软删除的租户列表（BaseMapper 查不到，需原生SQL） */
    @Select("SELECT * FROM sys_tenant WHERE deleted = 1 ORDER BY id")
    List<SysTenant> deletedList();
}
