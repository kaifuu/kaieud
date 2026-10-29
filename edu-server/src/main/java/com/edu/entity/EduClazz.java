package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("edu_clazz")
public class EduClazz {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    private Long courseId;
    /** 班主任(教师档案ID) */
    private Long classTeacherId;
    private Long orgId;
    private Long classroomId;
        private Integer capacity;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 1招生中 2开课中 3已结课 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
