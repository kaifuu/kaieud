package com.edu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.entity.EduScoreLog;
import org.apache.ibatis.annotations.Mapper;

/** 学员积分流水 Mapper */
@Mapper
public interface EduScoreLogMapper extends BaseMapper<EduScoreLog> {
}
