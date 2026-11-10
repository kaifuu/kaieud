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
    /** 所属校区（组织树ID，历史字段：新数据按校区字典名存 campus） */
    private Long orgId;
    /** 所属校区名称（来源系统字典-校区） */
    private String campus;
    private String name;
    private Integer capacity;
    /** 联系人 */
    private String contact;
    /** 联系电话 */
    private String contactPhone;
    /** 备注（教室位置、设备、使用注意事项等） */
    private String remark;
    private Integer deleted;
    private LocalDateTime createTime;
}
