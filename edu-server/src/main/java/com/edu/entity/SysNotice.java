package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notice")
public class SysNotice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String title;
    private String content;
    /** 0系统 1课程 2缴费 */
    private Integer noticeType;
    /** 0普通 1重要 2紧急 */
    private Integer level;
    /** 目标角色: ALL/EDU_ADMIN/CLASS_TEACHER/TEACHER/STUDENT */
    private String target;
    /** 0草稿 1已发布 */
    private Integer status;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
}
