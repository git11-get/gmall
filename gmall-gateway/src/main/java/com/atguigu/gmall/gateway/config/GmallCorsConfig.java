package com.atguigu.gmall.gateway.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class GmallCorsConfig {


    @Bean
    public CorsWebFilter corsWebFilter(){
        //cors跨域配置对象
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.addAllowedOrigin("http://localhost:1000");
        configuration.setAllowCredentials(true);
        //允许的提交的方式方法，就是如：get,post,put.....
        configuration.addAllowedMethod("*");
        //允许任何的头信息
        configuration.addAllowedHeader("*");

        //配置源对象
        UrlBasedCorsConfigurationSource configurationSource = new UrlBasedCorsConfigurationSource();
        configurationSource.registerCorsConfiguration("/**", configuration);
        //cors过滤器对象
        return new CorsWebFilter(configurationSource);
    }
}
