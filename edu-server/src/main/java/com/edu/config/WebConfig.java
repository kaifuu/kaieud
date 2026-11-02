package com.edu.config;

import com.edu.security.AuthInterceptor;
import com.edu.security.PlatformInternalBlockInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final PlatformInternalBlockInterceptor platformInternalBlockInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/captcha", "/sys/config/public", "/sys/user/*/avatar", "/error");
        // 平台运营与企业内部数据的写边界（读操作保留做诊断；X-Target-Tenant 切换视角后可代操作）
        registry.addInterceptor(platformInternalBlockInterceptor)
                .addPathPatterns("/sys/user/**", "/sys/role/**", "/sys/org/**", "/sys/menu/**");
    }

    @Override
    public void addResourceHandlers(org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry registry) {
        // 上传文件静态访问：/uploads/xxx -> ./uploads/xxx
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
