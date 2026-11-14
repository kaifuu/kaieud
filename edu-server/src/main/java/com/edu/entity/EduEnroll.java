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
    /** 报名校区（edu_course_campus 校区名，一单一校区；套餐订阅无课程时为空） */
    private String campus;
    /** 订阅套餐ID（按课程订阅时为空） */
    private Long packageId;
    private Long clazzId;
    private Long teacherId;
    private Integer totalHours;
    private Integer usedHours;
    private BigDecimal price;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    /** 优惠金额（报名时录入，各明细合计） */
    private BigDecimal discountAmount;
    /** 优惠明细JSON [{type,amount}]：编辑时按原样恢复多行优惠，不合并 */
    private String discountDetail;
    private BigDecimal paidAmount;
    /** 到款状态：0未确认 1已确认到款（办理中心手动确认，与金额推导并存） */
    private Integer payStatus;
    /** 支付明细JSON [{channel,account,serial,amount}] */
    private String payDetail;
    /** 1在读 2已结课 3已退订 4已转课 */
    private Integer status;
    private LocalDateTime createTime;
    /** 编辑时间（DB ON UPDATE 自动刷新，只读展示用） */
    private LocalDateTime updateTime;
    /** 有效期（报名时勾选指定） */
    private java.time.LocalDate validDate;
    /** 停课日期（status=6 停课时记录，复课清空） */
    private java.time.LocalDate suspendDate;
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
    /** 数量单位（查询时填充：课时/天/周/月，按课程真实收费模式推导，非表字段） */
    @TableField(exist = false)
    private String hoursUnit;
}
