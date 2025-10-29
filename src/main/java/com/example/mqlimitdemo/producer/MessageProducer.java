package com.example.mqlimitdemo.producer;

import com.alibaba.fastjson.JSON;
import com.example.mqlimitdemo.domain.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 消息生产者
 *
 * @author demo
 */
@Slf4j
@Component
public class MessageProducer {

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 发送同步消息
     *
     * @param topic   主题
     * @param message 消息内容
     * @return 发送结果
     */
    public SendResult sendSyncMessage(String topic, String message) {
        log.info("发送同步消息到 Topic: {}, 消息内容: {}", topic, message);
        SendResult sendResult = rocketMQTemplate.syncSend(topic, message);
        log.info("消息发送成功，MsgId: {}", sendResult.getMsgId());
        return sendResult;
    }

    /**
     * 发送异步消息
     *
     * @param topic   主题
     * @param message 消息内容
     */
    public void sendAsyncMessage(String topic, String message) {
        log.info("发送异步消息到 Topic: {}, 消息内容: {}", topic, message);
        rocketMQTemplate.asyncSend(topic, message, new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("异步消息发送成功，MsgId: {}", sendResult.getMsgId());
            }

            @Override
            public void onException(Throwable e) {
                log.error("异步消息发送失败: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 发送单向消息（不关心发送结果）
     *
     * @param topic   主题
     * @param message 消息内容
     */
    public void sendOneWayMessage(String topic, String message) {
        log.info("发送单向消息到 Topic: {}, 消息内容: {}", topic, message);
        rocketMQTemplate.sendOneWay(topic, message);
        log.info("单向消息已发送");
    }

    /**
     * 发送带 Tag 的消息
     *
     * @param topic   主题
     * @param tag     标签
     * @param message 消息内容
     * @return 发送结果
     */
    public SendResult sendMessageWithTag(String topic, String tag, String message) {
        String destination = topic + ":" + tag;
        log.info("发送带Tag的消息到 {}, 消息内容: {}", destination, message);
        SendResult sendResult = rocketMQTemplate.syncSend(destination, message);
        log.info("带Tag消息发送成功，MsgId: {}", sendResult.getMsgId());
        return sendResult;
    }

    /**
     * 发送对象消息
     *
     * @param topic        主题
     * @param orderMessage 订单消息对象
     * @return 发送结果
     */
    public SendResult sendObjectMessage(String topic, OrderMessage orderMessage) {
        log.info("发送对象消息到 Topic: {}", topic);
        String jsonMessage = JSON.toJSONString(orderMessage);
        Message<String> message = MessageBuilder.withPayload(jsonMessage).build();
        SendResult sendResult = rocketMQTemplate.syncSend(topic, message);
        log.info("对象消息发送成功，MsgId: {}, 订单ID: {}", 
                sendResult.getMsgId(), orderMessage.getOrderId());
        return sendResult;
    }

    /**
     * 发送延迟消息
     *
     * @param topic      主题
     * @param message    消息内容
     * @param delayLevel 延迟级别 (1-18)
     *                   1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     * @return 发送结果
     */
    public SendResult sendDelayMessage(String topic, String message, int delayLevel) {
        log.info("发送延迟消息到 Topic: {}, 延迟级别: {}, 消息内容: {}", topic, delayLevel, message);
        Message<String> msg = MessageBuilder.withPayload(message).build();
        SendResult sendResult = rocketMQTemplate.syncSend(topic, msg, 3000, delayLevel);
        log.info("延迟消息发送成功，MsgId: {}", sendResult.getMsgId());
        return sendResult;
    }
}

