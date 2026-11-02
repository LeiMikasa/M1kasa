package com.m1kasa.m1kasacontentservice.mq;

import com.m1kasa.common.util.DeadLetterMessageKey;
import com.m1kasa.m1kasacontentservice.entity.BlogLikeDeadLetter;
import com.rabbitmq.client.LongString;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

@Component
public class BlogLikeDeadLetterRecordFactory {
    private final JsonMapper jsonMapper;

    public BlogLikeDeadLetterRecordFactory(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public BlogLikeDeadLetter from(
        String sourceQueue,
        Message message
    ){
        if (message==null){
            throw new IllegalArgumentException("消息不能为空");
        }
        MessageProperties properties = message.getMessageProperties();
        BlogLikeDeadLetter record = new BlogLikeDeadLetter();
        //1.计算稳定的消息指纹
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
        //3.保存原始消息字节
        record.setBodyBase64(
                Base64.getEncoder().encodeToString(message.getBody())
        );
        //4.保存内容类型和编码
        record.setContentType(properties.getContentType());
        record.setContentEncoding(properties.getContentEncoding());
        //5.将消息体头转换为可保存的JSON
        record.setHeadersJson(
                jsonMapper.writeValueAsString(normalize(properties.getHeaders()))
        );
        //6.提取最近一次死信原因
        record.setDeathReason(readDeathReason(properties));
        return record;
    }
    private String readDeathReason(
            MessageProperties properties
    ) {
        List<Map<String, ?>> deaths =
                properties.getXDeathHeader();

        if (deaths == null || deaths.isEmpty()) {
            return null;
        }

        Object reason = deaths.get(0).get("reason");

        return reason == null
                ? null
                : reason.toString();
    }

    private Object normalize(Object value){
        //RabbitMq特殊字符转换为普通字符串
        if (value instanceof LongString text){
            return text.toString();
        }
        //递归处理嵌套的Map
        if (value instanceof Map<?,?> map){
            Map<String,Object> result=new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(
                        String.valueOf(entry.getKey()),
                        normalize(entry.getValue())
                );
            }
            return result;
        }

        //递归处理列表，例如x-death
        if (value instanceof List<?> list){
            List<Object> result=new ArrayList<>();
            for (Object item : list) {
                result.add(normalize(item));
            }
            return result;
        }
        return value;
    }



}
