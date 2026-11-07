package com.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.entity.EduCourseCampus;
import org.apache.ibatis.annotations.Mapper;

/** 课程-校区关联（多对多） */
@Mapper
public interface EduCourseCampusMapper extends BaseMapper<EduCourseCampus> {
}
