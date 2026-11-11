package com.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.entity.EduAuditionStudent;
import org.apache.ibatis.annotations.Mapper;

/** 班级试听学员 */
@Mapper
public interface EduAuditionStudentMapper extends BaseMapper<EduAuditionStudent> {
}
