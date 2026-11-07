package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 课程-校区关联（多对多中间表）：课程保存时物化，"全部校区"按当期校区字典展开为多行 */
@Data
@TableName("edu_course_campus")
public class EduCourseCampus {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long courseId;
    /** 校区名（sys_dict campus，不含"全部校区"） */
    private String campus;
}
