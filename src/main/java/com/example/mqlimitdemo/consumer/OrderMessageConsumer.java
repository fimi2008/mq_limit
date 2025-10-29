package com.example.mqlimitdemo.consumer;

import com.alibaba.fastjson.JSON;
import com.example.mqlimitdemo.domain.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 订单消息消费者
 * 
 * Topic: order-topic
 * ConsumerGroup: demo-consumer-group
 *
 * @author demo
 */
@Slf4j
@Component
@RocketMQMessageListener(
        // 消费的Topic
        topic = "order-topic",
        // 消费者组名
        consumerGroup = "demo-consumer-group",
        // 消息模式：集群消费（默认）
        messageModel = MessageModel.CLUSTERING,
        // 消费模式：并发消费（默认）
        consumeMode = ConsumeMode.CONCURRENTLY,
        // 最大消费线程数
        consumeThreadMax = 20
)
public class OrderMessageConsumer implements RocketMQListener<String> {

    @Override
    public void onMessage(String message) {
        log.info("========== 开始消费消息 ==========");
        log.info("接收到的原始消息: {}", message);
        
        try {
            // 解析消息
            OrderMessage orderMessage = JSON.parseObject(message, OrderMessage.class);
            log.info("解析后的订单信息: {}", orderMessage);
            
            // 业务处理
            processOrder(orderMessage);
            
            log.info("订单 {} 处理成功", orderMessage.getOrderId());
            
        } catch (Exception e) {
            log.error("消息消费失败: {}", e.getMessage(), e);
            // 抛出异常，消息会重新消费
            throw new RuntimeException("消息消费失败", e);
        } finally {
            log.info("========== 消息消费结束 ==========\n");
        }
    }

    /**
     * 处理订单业务逻辑
     *
     * @param orderMessage 订单消息
     */
    private void processOrder(OrderMessage orderMessage) {
        // 模拟业务处理
        log.info("正在处理订单...");
        log.info("订单ID: {}", orderMessage.getOrderId());
        log.info("用户ID: {}", orderMessage.getUserId());
        log.info("商品名称: {}", orderMessage.getProductName());
        log.info("订单金额: {}", orderMessage.getAmount());
        log.info("订单状态: {}", orderMessage.getStatus());
        
        // 模拟耗时操作
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("订单处理完成");
    }
}

