package com.atguigu.gmall.wms.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMqConfig {

    @Bean("WMS-TTL-QUEUE")
    public Queue ttlQueue(){ //延迟队列

        Map<String, Object> map = new HashMap<>();
        map.put("x-dead-letter-exchange", "GMALL-ORDER-EXCHANGE");
        map.put("x-dead-letter-routing-key","stock.unlock");
        map.put("x-message-ttl",90000); //毫秒

        return new Queue("WMS-TTL-QUEUE",true,false,false,map);
    }

    @Bean("WMS-TTL-BINDING")
    public Binding ttlBinding(){ //延迟队列绑定到交换机

        return new Binding("WMS-TTL-QUEUE",Binding.DestinationType.QUEUE,"GMALL-ORDER-EXCHANGE","stock.ttl",null);
    }

    /*@Bean("WMS-DEAD-QUEUE")
    public Queue dlQueue(){ //死信对列
        return new Queue("WMS-DEAD-QUEUE",true,false,false,null);
    }*/

    /*@Bean("WMS-DEAD-BINDING")
    public Binding deadBinding(){ //延迟队列绑定到交换机

        return new Binding("WMS-DEAD-QUEUE",Binding.DestinationType.QUEUE,"GMALL-ORDER-EXCHANGE","stock.dead",null);
    }*/



}



