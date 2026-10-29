package com.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("SELECT avatar FROM sys_user WHERE id = #{id}")
    java.util.Map<String, Object> selectAvatarRaw(@Param("id") Long id);

    @Update("UPDATE sys_user SET avatar = #{avatar} WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatar") byte[] avatar);
}
