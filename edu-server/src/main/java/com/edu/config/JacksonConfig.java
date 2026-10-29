package com.edu.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
                .deserializers(new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .deserializers(new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }
}
