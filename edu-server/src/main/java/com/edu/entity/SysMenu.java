package com.edu.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId
    private Long id;
    /** 租户ID：NULL=平台模板；租户自有菜单各租户隔离 */
    private Long tenantId;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private String perm;
    /** M目录 C菜单 */
    private String type;
    private Integer sort;
    /** 菜单范围：0=租户模板（租户可见/复制） 1=平台专属（仅平台运营可见） */
    private Integer scope;
    private Integer visible;
}
