package com.m1kasa.m1kasaorderservice.mq;

import com.m1kasa.m1kasaorderservice.entity.MqDeadLetter;
import com.m1kasa.m1kasaorderservice.util.DeadLetterMessageKey;
import com.rabbitmq.client.LongString;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

@Component
public class DeadLetterRecordFactory {

    private final JsonMapper jsonMapper;

    public DeadLetterRecordFactory(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public MqDeadLetter from(
            String sourceQueue,
            Message message
    ){
        if (message==null){
            throw new IllegalArgumentException("消息不能为空");
        }
        MessageProperties properties =
                message.getMessageProperties();
        MqDeadLetter record=new MqDeadLetter();
        //1.计算消息指纹
        record.setMessageKey(
                DeadLetterMessageKey.calculate(
                        sourceQueue,
                        properties.getMessageId(),
                        message.getBody()
                )
        );

        //2.保存消息ID和原业务队列
        record.setMessageId(properties.getMessageId());
        record.setSourceQueue(sourceQueue);

        //3.原始消息体转换成Base64字符串
        record.setBodyBase64(
                Base64.getEncoder().encodeToString(message.getBody())
        );
        //4.保存内容类型和编码
        record.setContentType(properties.getContentType());
        record.setContentEncoding(properties.getContentEncoding());
        //5.将消息头转换为可保存的JSON
        record.setHeadersJson(
                jsonMapper.writeValueAsString(
                     normalize(properties.getHeaders())
                )
        );
        //6.提取最近一次死信原因
        record.setDeathReason(readDeathReason(properties));
        return record;
    }
    private String readDeathReason(MessageProperties properties) {
        List<Map<String, ?>> deaths = properties.getXDeathHeader();

        if (deaths == null || deaths.isEmpty()) {
            return null;
        }

        Object reason = deaths.get(0).get("reason");

        return reason == null ? null : reason.toString();
    }


    private Object normalize(Object value){
        //rabbitmq特殊字符串类型转换为普通字符串
        if (value instanceof LongString text){
            return text.toString();
        }
        //消息头中可能还有嵌套Map
        if (value instanceof Map<?,?> map){
            Map<String,Object> results=new LinkedHashMap<>();

        }// 消息头中可能还有嵌套 Map
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(
                        String.valueOf(entry.getKey()),
                        normalize(entry.getValue())
                );
            }

            return result;
        }

        // x-death 等消息头中可能包含列表
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>();

            for (Object item : list) {
                result.add(normalize(item));
            }

            return result;
        }

        // 数字、普通字符串等交给 JSON 转换器处理
        return value;
    }
}


