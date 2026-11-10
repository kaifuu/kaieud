package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
    /** 班主任候选员工(用户ID，非表字段)：保存时后端解析/补建教师档案后回填 classTeacherId */
    @TableField(exist = false)
    private Long classTeacherUserId;
    private Long orgId;
    private Long classroomId;
    /** 满班人数（最多容纳学生数） */
    private Integer capacity;
    /** 最少开班人数（低于此人数不宜开班） */
    private Integer minCapacity;
    /** 每次上课：学生扣除课时 */
    private java.math.BigDecimal studentHours;
    /** 每次上课：教师获得课时 */
    private java.math.BigDecimal teacherHours;
    /** 开班日期 */
    private LocalDate startDate;
    /** 结班日期 */
    private LocalDate endDate;
    /** 1招生中 2开课中 3已结课 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
