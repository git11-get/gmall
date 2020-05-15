package com.atguigu.gmall.oms.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    @Bean("ORDER-TTL-QUEUE")
    public Queue ttlQueue(){ //延迟队列

        Map<String, Object> map = new HashMap<>();
        map.put("x-dead-letter-exchange", "GMALL-ORDER-EXCHANGE");
        map.put("x-dead-letter-routing-key","order.dead");
        map.put("x-message-ttl",120000); //毫秒

        return new Queue("ORDER-TTL-QUEUE",true,false,false,map);
    }

    @Bean("ORDER-TTL-BINDING")
    public Binding ttlBinding(){ //延迟队列绑定到交换机

        return new Binding("ORDER-TTL-QUEUE",Binding.DestinationType.QUEUE,"GMALL-ORDER-EXCHANGE","order.ttl",null);
    }

    @Bean("ORDER-DEAD-QUEUE")
    public Queue dlQueue(){ //死信对列


        return new Queue("ORDER-DEAD-QUEUE",true,false,false,null);
    }

    @Bean("ORDER-DEAD-BINDING")
    public Binding deadBinding(){ //延迟队列绑定到交换机

        return new Binding("ORDER-DEAD-QUEUE",Binding.DestinationType.QUEUE,"GMALL-ORDER-EXCHANGE","order.dead",null);
    }



}



