package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class SysTenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String code;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
