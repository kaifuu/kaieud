package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 合同操作日志：新增/修改/退费/删除全量留痕（合同历史TAB数据源，物理删除后仍可追溯） */
@Data
@TableName("edu_contract_log")
public class EduContractLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long contractId;
    private String contractNo;
    private Long studentId;
    /** 新增 / 修改 / 退费 / 删除 */
    private String action;
    /** 操作内容摘要 */
    private String content;
    private String operator;
    private LocalDateTime createTime;
}
