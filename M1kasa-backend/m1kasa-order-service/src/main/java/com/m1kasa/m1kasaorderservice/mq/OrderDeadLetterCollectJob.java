package com.m1kasa.m1kasaorderservice.mq;


import com.m1kasa.m1kasaorderservice.service.MqDeadLetterService;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.support.DefaultMessagePropertiesConverter;
import org.springframework.amqp.rabbit.support.MessagePropertiesConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "m1kasa.order.dead-letter",
        name = "enabled",
        havingValue = "true"
)
public class OrderDeadLetterCollectJob {

    private static final Logger log=
            LoggerFactory.getLogger(OrderDeadLetterCollectJob.class);

    private static final String DEAD_LETTER_QUEUE=
            "m1kasa.seckill.order.dlq";
    private static final int BATCH_SIZE=20;

    private final RabbitTemplate rabbitTemplate;
    private final MqDeadLetterService deadLetterService;

    private final MessagePropertiesConverter propertiesConverter =
            new DefaultMessagePropertiesConverter();

    public OrderDeadLetterCollectJob(RabbitTemplate rabbitTemplate, MqDeadLetterService deadLetterService) {
        this.rabbitTemplate = rabbitTemplate;
        this.deadLetterService = deadLetterService;
    }

    @Scheduled(fixedDelay = 5000,initialDelay = 5000)
    public void collect(){
        try {
            //1.拿到mq通道,通过它操作消息
            rabbitTemplate.execute(channel -> {
                //操作消息
                for (int i=0;i<BATCH_SIZE;i++){
                    //从死信队列中取一条消息
                    GetResponse response = channel.basicGet(
                            DEAD_LETTER_QUEUE, //从哪个队列区消息，这里是订单死信队列
                            false   //不自动确认，后面由代码手动确认
                    );
                    //当前没有消息，结束本批
                    if (response==null){
                        break;
                    }
                    //获取这次投递的编号 deliveryTag 是 RabbitMQ 给这次投递分配的标识，后面用它告诉 RabbitMQ
                    long deliveryTag = response.getEnvelope().getDeliveryTag();
                    try {
                        //把 RabbitMQ 客户端的属性结构，转换成 Spring AMQP 使用的 MessageProperties。
                        MessageProperties properties = propertiesConverter.toMessageProperties(
                                response.getProps(),        //消息ID，内容类型，消息头
                                response.getEnvelope(),     //投递和路由信息
                                "UTF-8"           //属性转换使用的字符编码
                        );
                        //组装 Spring 的原始消息对象
                        Message message=new Message(
                                response.getBody(),
                                properties
                        );
                        //通过独立Service 调用，返回前完成数据库事务提交
                        deadLetterService.saveOrderMessage(message);
                    }catch (RuntimeException ex){
                        log.warn(
                                "订单死信保存失败，重新入队，messageId={}",
                                response.getProps().getMessageId(),
                                ex
                        );
                        //只拒绝当前消息，并让他重新入队
                        channel.basicNack(
                                deliveryTag, //处理哪一次投递
                                false, //只处理当前这一条，不批量处理
                                true);  //要求重新入队
                        // 保存失败就结束本批，等待下一轮再试
                        return null;
                    }
                    // 只确认当前消息
                    channel.basicAck(deliveryTag, false);
                }
                return null;
            });
        }catch (RuntimeException ex){
             log.warn("采集订单死信时发生异常",ex);
        }
    }
}
