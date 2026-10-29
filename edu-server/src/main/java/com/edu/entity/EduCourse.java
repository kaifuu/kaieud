package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("edu_course")
public class EduCourse {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    /** 1班课 2一对一 */
    private Integer courseType;
    private String subject;
    private Integer totalHours;
    private BigDecimal price;
    private BigDecimal discount;
    /** 1文化课2艺术3素养 */
    private Integer category;
    /** 1一对一2小班3大班 */
    private Integer classType;
    private Integer durationMinutes;
    private Integer deleted;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
