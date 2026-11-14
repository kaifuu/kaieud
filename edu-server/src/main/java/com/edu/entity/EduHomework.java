package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 班级作业（班级管理-作业TAB 布置） */
@Data
@TableName("edu_homework")
public class EduHomework {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long clazzId;
    private String title;
    private String content;
    /** 图片/视频/录音附件 JSON[{url,name}] */
    private String images;
    private String videos;
    private String audios;
    private Long teacherId;
    /** 发送学员ID集（逗号分隔，空=全班在读） */
    private String targetStudents;
    /** 截止日期 */
    private LocalDate deadline;
    /** 0开放 1已关闭 2草稿 */
    private Integer status;
    /** 学员提交后对本班可见：0否 1是 */
    private Integer visibleAll;
    /** 家长端可提交：0否 1是 */
    private Integer parentSubmit;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
