package com.edu.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** java.time 序列化/反序列化全局格式 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .serializers(new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .serializers(new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .deserializerByType(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer())
                .deserializers(new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }

    /**
     * 宽松 LocalDateTime 反序列化：
     * yyyy-MM-dd HH:mm:ss（标准）/ ISO 格式（含 T、毫秒）/ 带时区偏移的 ISO 串
     * （如日历组件拖拽产生的 2026-10-29T10:00:00+08:00，按系统时区换算为本地时间）。
     */
    static class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter[] FORMATTERS = {
                DateTimeFormatter.ISO_LOCAL_DATE_TIME, // T 格式（含可选秒/毫秒）
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
        };

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String text = p.getText();
            if (text == null || text.trim().isEmpty()) return null;
            text = text.trim();
            // 带时区偏移（+08:00 / +0800 / Z）：按系统时区转为本地时间
            if (text.endsWith("Z") || text.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                try {
                    return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                            .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
                } catch (DateTimeParseException ignore) { /* 尝试普通格式 */ }
            }
            for (DateTimeFormatter f : FORMATTERS) {
                try { return LocalDateTime.parse(text, f); } catch (DateTimeParseException ignore) { }
            }
            throw ctxt.weirdStringException(text, LocalDateTime.class,
                    "日期格式应为 yyyy-MM-dd HH:mm:ss 或 ISO 格式");
        }
    }
}
