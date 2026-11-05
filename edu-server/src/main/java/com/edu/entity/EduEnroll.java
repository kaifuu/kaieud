package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("edu_enroll")
public class EduEnroll {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long studentId;
    private Long courseId;
    /** 订阅套餐ID（按课程订阅时为空） */
    private Long packageId;
    private Long clazzId;
    private Long teacherId;
    private Integer totalHours;
    private Integer usedHours;
    private BigDecimal price;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    /** 优惠金额（报名时录入） */
    private BigDecimal discountAmount;
    private BigDecimal paidAmount;
    /** 1在读 2已结课 3已退费 */
    private Integer status;
    private LocalDateTime createTime;
    /** 有效期（报名时勾选指定） */
    private java.time.LocalDate validDate;
    /** 备注（对内） */
    private String remark;
    /** 销售来源 */
    private String source;
    /** 经办人 */
    private String handler;
    /** 经办日期 */
    private java.time.LocalDate handleDate;
    /** 采单员 */
    private String orderTaker;
    /** 咨询顾问 */
    private String consultant;
    /** 套餐名称（查询时填充，非表字段） */
    @TableField(exist = false)
    private String packageName;
}
