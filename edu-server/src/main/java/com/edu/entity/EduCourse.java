package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("edu_course")
public class EduCourse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    /** 1班课 2一对一（历史字段，页面已用班型 classType） */
    private Integer courseType;
    private String subject;
    /** 适用年级（字典 grade） */
    private String grade;
    /** 默认授课老师 */
    private Long teacherId;
    private Integer totalHours;
    private BigDecimal price;
    private BigDecimal discount;
    /** 科目（字典 course_category：语文/数学/英语…） */
    private Integer category;
    /** 班型（字典 class_type） */
    private Integer classType;
    /** 授课模式（字典 teach_mode：一对一/小班课/大班课/线上课） */
    private Integer teachMode;
    /** 开课校区（指定模式为逗号分隔中文名，空=全部校区） */
    private String campus;
    /** 校区模式 1全部校区 2指定校区 */
    private Integer campusMode;
    /** 按课时收费 0/1 */
    private Integer chargeHour;
    /** 按时间收费 0/1 */
    private Integer chargeTime;
    private Integer durationMinutes;
    /** 满班人数 */
    private Integer capacity;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer deleted;
    /** 1上架 0下架 */
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    /** 更新时间（库表 ON UPDATE CURRENT_TIMESTAMP 自动维护） */
    private LocalDateTime updateTime;
    /** 定价标准（保存时随课程提交；查询走 /edu/course/{id}/prices） */
    @TableField(exist = false)
    private java.util.List<EduCoursePrice> prices;
}
