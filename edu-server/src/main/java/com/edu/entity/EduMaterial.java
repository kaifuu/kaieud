package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 教材杂费项目（办理中心-教材杂费可选项） */
@Data
@TableName("edu_material")
public class EduMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 项目名称（教材/杂费） */
    private String name;
    /** 单价（元） */
    private BigDecimal price;
    /** 1上架 0停用 */
    private Integer status;
    private Integer sort;
    private LocalDateTime createTime;
}
