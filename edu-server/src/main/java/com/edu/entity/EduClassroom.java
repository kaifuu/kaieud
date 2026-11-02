package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 教室（1个教室可对应多个班级/课次） */
@Data
@TableName("edu_classroom")
public class EduClassroom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long orgId;
    private String name;
    private Integer capacity;
    /** 备注（教室位置、设备、使用注意事项等） */
    private String remark;
    private Integer deleted;
    private LocalDateTime createTime;
}
