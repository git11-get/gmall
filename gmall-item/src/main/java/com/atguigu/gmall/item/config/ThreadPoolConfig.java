package com.atguigu.gmall.item.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor threadPoolExecutor(){
        // 参数1：线程池连接数
        // 参数2：线程池最大连接数
        // 参数3：生存时间
        // 参数4：时间单位
        // 参数5：阻塞队列(阻塞等待的排队数)

        // 线程池初始化
        return new ThreadPoolExecutor(50,500,30,TimeUnit.SECONDS,new ArrayBlockingQueue<>(10000));
    }
}
