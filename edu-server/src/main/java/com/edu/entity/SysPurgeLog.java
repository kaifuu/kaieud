package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 企业数据物理清除审计：谁、什么时间、清除了哪个企业（不可恢复操作必须留痕） */
@Data
@TableName("sys_purge_log")
public class SysPurgeLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String tenantName;
    private String operator;
    private String operation;
    /** 清除明细（各表删除行数） */
    private String detail;
    private LocalDateTime createTime;
}
