package com.example.mqlimitdemo.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 简单消息消费者
 * 
 * Topic: simple-topic
 * ConsumerGroup: simple-consumer-group
 *
 * @author demo
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "simple-topic",
        consumerGroup = "simple-consumer-group"
)
public class SimpleMessageConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("========== 简单消息消费者 ==========");
        log.info("接收到消息: {}", message);
        log.info("消息处理完成");
        log.info("====================================\n");
    }
}

