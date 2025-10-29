package com.example.mqlimitdemo.controller;

import com.example.mqlimitdemo.domain.OrderMessage;
import com.example.mqlimitdemo.producer.MessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 消息发送控制器
 *
 * @author demo
 */
@Slf4j
@RestController
@RequestMapping("/message")
public class MessageController {

    @Resource
    private MessageProducer messageProducer;

    /**
     * 发送简单消息
     *
     * @param message 消息内容
     * @return 结果
     */
    @GetMapping("/send/simple")
    public Map<String, Object> sendSimpleMessage(@RequestParam(defaultValue = "Hello RocketMQ!") String message) {
        SendResult result = messageProducer.sendSyncMessage("simple-topic", message);
        return buildResponse(true, "消息发送成功", result);
    }

    /**
     * 发送订单消息
     *
     * @return 结果
     */
    @PostMapping("/send/order")
    public Map<String, Object> sendOrderMessage() {
        OrderMessage orderMessage = new OrderMessage();
        orderMessage.setOrderId("ORDER-" + UUID.randomUUID().toString().substring(0, 8));
        orderMessage.setUserId("USER-" + System.currentTimeMillis());
        orderMessage.setProductName("MacBook Pro");
        orderMessage.setAmount(new BigDecimal("12999.00"));
        orderMessage.setStatus("PENDING");
        orderMessage.setCreateTime(new Date());
        orderMessage.setRemark("这是一个测试订单");

        SendResult result = messageProducer.sendObjectMessage("order-topic", orderMessage);
        return buildResponse(true, "订单消息发送成功", result);
    }

    /**
     * 发送异步消息
     *
     * @param message 消息内容
     * @return 结果
     */
    @GetMapping("/send/async")
    public Map<String, Object> sendAsyncMessage(@RequestParam(defaultValue = "Async Message") String message) {
        messageProducer.sendAsyncMessage("simple-topic", message);
        return buildResponse(true, "异步消息已提交", null);
    }

    /**
     * 发送单向消息
     *
     * @param message 消息内容
     * @return 结果
     */
    @GetMapping("/send/oneway")
    public Map<String, Object> sendOneWayMessage(@RequestParam(defaultValue = "OneWay Message") String message) {
        messageProducer.sendOneWayMessage("simple-topic", message);
        return buildResponse(true, "单向消息已发送", null);
    }

    /**
     * 发送带Tag的消息
     *
     * @param tag     标签
     * @param message 消息内容
     * @return 结果
     */
    @GetMapping("/send/tag")
    public Map<String, Object> sendMessageWithTag(
            @RequestParam(defaultValue = "tagA") String tag,
            @RequestParam(defaultValue = "Tagged Message") String message) {
        SendResult result = messageProducer.sendMessageWithTag("tag-topic", tag, message);
        return buildResponse(true, "带Tag消息发送成功", result);
    }

    /**
     * 发送延迟消息
     *
     * @param message    消息内容
     * @param delayLevel 延迟级别 (1-18)
     * @return 结果
     */
    @GetMapping("/send/delay")
    public Map<String, Object> sendDelayMessage(
            @RequestParam(defaultValue = "Delay Message") String message,
            @RequestParam(defaultValue = "3") int delayLevel) {
        SendResult result = messageProducer.sendDelayMessage("simple-topic", message, delayLevel);
        return buildResponse(true, "延迟消息发送成功", result);
    }

    /**
     * 批量发送消息
     *
     * @param count 发送数量
     * @return 结果
     */
    @GetMapping("/send/batch")
    public Map<String, Object> sendBatchMessage(@RequestParam(defaultValue = "10") int count) {
        log.info("批量发送 {} 条消息", count);
        for (int i = 0; i < count; i++) {
            String message = "Batch Message " + (i + 1);
            messageProducer.sendSyncMessage("simple-topic", message);
        }
        return buildResponse(true, "批量发送完成，共发送 " + count + " 条消息", null);
    }

    /**
     * 构建响应结果
     */
    private Map<String, Object> buildResponse(boolean success, String message, SendResult sendResult) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("timestamp", System.currentTimeMillis());
        if (sendResult != null) {
            response.put("msgId", sendResult.getMsgId());
            response.put("sendStatus", sendResult.getSendStatus());
        }
        return response;
    }
}

