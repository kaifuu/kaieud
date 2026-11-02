package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 字典类型中文名（字典管理左侧类型列表显示名，可维护、持久化） */
@Data
@TableName("sys_dict_type")
public class SysDictType {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID：0=平台模板；各企业自维护一套 */
    private Long tenantId;
    /** 类型标识（对应 sys_dict.dict_type） */
    private String dictType;
    /** 类型中文名 */
    private String name;
}
