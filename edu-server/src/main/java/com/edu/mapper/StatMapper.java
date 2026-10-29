package com.edu.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** 统计汇总查询(见 mapper/StatMapper.xml) */
@Mapper
public interface StatMapper {

    /** 按学校维度统计收入 */
    List<Map<String, Object>> revenueBySchool(@Param("tenantId") Long tenantId,
                                              @Param("begin") String begin, @Param("end") String end);

    /** 按教师维度统计收入(缴费归属到报名合同上的授课老师) */
    List<Map<String, Object>> revenueByTeacher(@Param("tenantId") Long tenantId,
                                               @Param("begin") String begin, @Param("end") String end);

    /** 按学生维度统计缴费 */
    List<Map<String, Object>> revenueByStudent(@Param("tenantId") Long tenantId,
                                               @Param("begin") String begin, @Param("end") String end);

    /** 教师投入量/闲置量：排课课时、已完成课时、授课学生数 */
    List<Map<String, Object>> teacherLoad(@Param("tenantId") Long tenantId,
                                          @Param("begin") String begin, @Param("end") String end);

    /** 课时占比：按课程统计已消耗课时 */
    List<Map<String, Object>> hoursShareByCourse(@Param("tenantId") Long tenantId);

    /** 近N日缴费趋势 */
    List<Map<String, Object>> revenueTrend(@Param("tenantId") Long tenantId,
                                           @Param("begin") String begin, @Param("end") String end);
}
