package com.edu.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 成本毛利报表（PRD固化公式：营收=合同单价×课时；成本=教师对应班型提成×课时；统计口径=消课时间） */
@Mapper
public interface CostMapper {

    /** 消课维度明细 */
    List<Map<String, Object>> detail(@Param("tenantId") Long tenantId,
                                     @Param("begin") String begin, @Param("end") String end,
                                     @Param("teacherId") Long teacherId,
                                     @Param("courseId") Long courseId,
                                     @Param("classType") Integer classType);

    /** 汇总：groupBy = teacher/course */
    List<Map<String, Object>> summary(@Param("tenantId") Long tenantId,
                                      @Param("begin") String begin, @Param("end") String end,
                                      @Param("groupBy") String groupBy,
                                      @Param("teacherId") Long teacherId,
                                      @Param("courseId") Long courseId,
                                      @Param("classType") Integer classType);

    /** 看板汇总 */
    Map<String, Object> dashboard(@Param("tenantId") Long tenantId,
                                  @Param("begin") String begin, @Param("end") String end,
                                  @Param("teacherId") Long teacherId,
                                  @Param("courseId") Long courseId,
                                  @Param("classType") Integer classType);
}
