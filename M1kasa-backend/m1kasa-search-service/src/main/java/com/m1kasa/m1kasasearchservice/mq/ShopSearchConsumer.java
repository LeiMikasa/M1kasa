package com.m1kasa.m1kasasearchservice.mq;

import com.m1kasa.m1kasasearchservice.service.ShopIndexService;
import com.m1kasa.shop.api.constant.ShopSearchMqConstants;
import com.m1kasa.shop.api.message.ShopSearchSyncMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ShopSearchConsumer {
    private static final Logger log=
            LoggerFactory.getLogger(ShopSearchConsumer.class);

    private final ShopIndexService shopIndexService;

    public ShopSearchConsumer(ShopIndexService shopIndexService) {
        this.shopIndexService = shopIndexService;
    }

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(
                            value = ShopSearchMqConstants.QUEUE,
                            durable = "true",
                            arguments = {
                                    @Argument(
                                            name = "x-dead-letter-exchange",
                                            value = ShopSearchMqConstants
                                                    .DEAD_LETTER_EXCHANGE
                                    ),
                                    @Argument(
                                            name = "x-dead-letter-routing-key",
                                            value = ShopSearchMqConstants
                                                    .DEAD_LETTER_ROUTING_KEY
                                    )
                            }
                    ),
                    exchange = @Exchange(
                            value = ShopSearchMqConstants.EXCHANGE,
                            type = ExchangeTypes.DIRECT,
                            durable = "true"
                    ),
                    key = ShopSearchMqConstants.ROUTING_KEY
            )
    )
    public void handle(
            ShopSearchSyncMessage message
    ) throws IOException {
        shopIndexService.sync(message);
        // 包括更新成功，以及重复或旧版本被安全跳过
        log.info(
                "店铺搜索消息处理完成，requestId={}, shopId={}, version={}",
                message.requestId(),
                message.shopId(),
                message.shopVersion()
        );
    }
}
