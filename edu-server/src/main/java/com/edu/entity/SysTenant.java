package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 租户：每个企业租户一套独立的组织/用户/角色/菜单/业务数据（行级 tenant_id 隔离） */
@Data
@TableName("sys_tenant")
public class SysTenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    /** 编码 = 租户管理员账号，创建后不可改 */
    private String code;
    private String contactPerson;
    private String contactPhone;
    /** 创建租户时自动生成的管理员账号（初始密码 123456） */
    private String adminUsername;
    private String address;
    /** 0-禁用 1-启用（禁用后租户内所有用户立即无法访问） */
    private Integer status;
    /** 过期时间：NULL=永久有效 */
    private LocalDateTime expireTime;
    /** 套餐编码 */
    private String packageCode;
    private String remark;
    /** 软删除：0-正常 1-已删除 */
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 租户是否可用：启用且未过期（登录与每次请求实时校验） */
    public boolean usable() {
        return status != null && status == 1
                && (expireTime == null || expireTime.isAfter(LocalDateTime.now()));
    }
}
