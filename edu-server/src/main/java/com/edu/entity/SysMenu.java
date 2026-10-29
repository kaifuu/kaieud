package com.edu.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId
    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private String perm;
    /** M目录 C菜单 */
    private String type;
    private Integer sort;
    private Integer visible;
}
