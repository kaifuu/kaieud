package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 考试成绩（班级管理-成绩TAB 录入；学员维度一条一考一项目） */
@Data
@TableName("edu_exam_score")
public class EduExamScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 录入时所在班级 */
    private Long clazzId;
    private Long studentId;
    /** 考试名称 */
    private String examName;
    /** 项目（如数学/英语/阶段测试） */
    private String item;
    private BigDecimal score;
    /** 来源：录入/导入 */
    private String source;
    /** 考试日期 */
    private LocalDate examDate;
    private String remark;
    private String operator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
