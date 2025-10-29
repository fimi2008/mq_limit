package com.example.mqlimitdemo.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * Tag 过滤消费者
 * 
 * 只消费 tag-topic 中带有 tagA 标签的消息
 *
 * @author demo
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "tag-topic",
        consumerGroup = "tag-consumer-group",
        // 设置标签过滤
        selectorType = SelectorType.TAG,
        selectorExpression = "tagA"
)
public class TagFilterConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("========== Tag过滤消费者 (只接收tagA) ==========");
        log.info("接收到带有 tagA 标签的消息: {}", message);
        log.info("===============================================\n");
    }
}

