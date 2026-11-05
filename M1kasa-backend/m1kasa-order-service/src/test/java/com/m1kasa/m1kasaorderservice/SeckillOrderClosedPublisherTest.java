package com.m1kasa.m1kasaorderservice;

import com.m1kasa.m1kasaorderservice.mq.SeckillOrderClosedPublisher;
import com.m1kasa.order.api.constant.OrderMqConstants;
import com.m1kasa.order.api.message.SeckillOrderClosedMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeckillOrderClosedPublisherTest {

    private static final SeckillOrderClosedMessage EVENT =
            new SeckillOrderClosedMessage(
                    "request-001", 10001L, 10L, 1011L, "STOCK_UNAVAILABLE"
            );

    private RabbitTemplate rabbitTemplate;
    private SeckillOrderClosedPublisher publisher;
    private final List<CorrelationData> attempts = new ArrayList<>();
    private final List<Message> messages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        publisher = new SeckillOrderClosedPublisher(rabbitTemplate);
        doAnswer(invocation -> {
            MessagePostProcessor processor = invocation.getArgument(3);
            Message processed = processor.postProcessMessage(
                    new Message(new byte[0], new MessageProperties())
            );
            messages.add(processed);
            attempts.add(invocation.getArgument(4));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(OrderMqConstants.EXCHANGE),
                eq(OrderMqConstants.SECKILL_ORDER_CLOSED_ROUTING_KEY),
                eq(EVENT),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );
    }

    @Test
    void waitsForConfirmationAndKeepsStableBusinessMessageIdOnRetry() {
        CompletableFuture<Void> first = publisher.send(EVENT);
        assertFalse(first.isDone());
        assertEquals("seckill-order-closed:request-001",
                messages.get(0).getMessageProperties().getMessageId());
        assertEquals(MessageDeliveryMode.PERSISTENT,
                messages.get(0).getMessageProperties().getDeliveryMode());

        attempts.get(0).getFuture().complete(new CorrelationData.Confirm(true, null));
        assertNull(first.join());

        CompletableFuture<Void> second = publisher.send(EVENT);
        assertEquals(messages.get(0).getMessageProperties().getMessageId(),
                messages.get(1).getMessageProperties().getMessageId());
        assertNotEquals(attempts.get(0).getId(), attempts.get(1).getId());
        attempts.get(1).getFuture().complete(new CorrelationData.Confirm(true, null));
        assertNull(second.join());
    }

    @Test
    void rejectsAnAckWhenMessageWasReturnedAsUnroutable() {
        CompletableFuture<Void> result = publisher.send(EVENT);
        CorrelationData attempt = attempts.get(0);
        attempt.setReturned(new ReturnedMessage(
                messages.get(0), 312, "NO_ROUTE",
                OrderMqConstants.EXCHANGE,
                OrderMqConstants.SECKILL_ORDER_CLOSED_ROUTING_KEY
        ));
        attempt.getFuture().complete(new CorrelationData.Confirm(true, null));

        CompletionException failure =
                assertThrows(CompletionException.class, result::join);
        assertTrue(failure.getCause().getMessage().contains("无法路由"));
    }

    @Test
    void rejectsBrokerNack() {
        CompletableFuture<Void> result = publisher.send(EVENT);
        attempts.get(0).getFuture().complete(
                new CorrelationData.Confirm(false, "broker rejected")
        );

        CompletionException failure =
                assertThrows(CompletionException.class, result::join);
        assertTrue(failure.getCause().getMessage().contains("broker rejected"));
    }

    @Test
    void exposesSynchronousSendFailureAsFailedFuture() {
        RuntimeException connectionFailure = new IllegalStateException("connection failed");
        doThrow(connectionFailure).when(rabbitTemplate).convertAndSend(
                eq(OrderMqConstants.EXCHANGE),
                eq(OrderMqConstants.SECKILL_ORDER_CLOSED_ROUTING_KEY),
                eq(EVENT),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        CompletableFuture<Void> result = publisher.send(EVENT);

        CompletionException failure =
                assertThrows(CompletionException.class, result::join);
        assertSame(connectionFailure, failure.getCause());
    }
}
