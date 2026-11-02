package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 通知阅读状态：按用户记录（点击查看内容后由未读变已读） */
@Data
@TableName("sys_notice_read")
public class SysNoticeRead {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long noticeId;
    private Long userId;
    private LocalDateTime readTime;
}
