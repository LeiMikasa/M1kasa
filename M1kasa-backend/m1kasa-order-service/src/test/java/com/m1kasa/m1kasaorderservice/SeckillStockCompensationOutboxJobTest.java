package com.m1kasa.m1kasaorderservice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.m1kasa.m1kasaorderservice.entity.SeckillStockCompensationOutbox;
import com.m1kasa.m1kasaorderservice.mapper.SeckillStockCompensationOutboxMapper;
import com.m1kasa.m1kasaorderservice.mq.SeckillOrderClosedPublisher;
import com.m1kasa.m1kasaorderservice.mq.SeckillStockCompensationOutboxJob;
import com.m1kasa.order.api.message.SeckillOrderClosedMessage;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeckillStockCompensationOutboxJobTest {

    private SeckillStockCompensationOutboxMapper mapper;
    private SeckillOrderClosedPublisher publisher;
    private SeckillStockCompensationOutboxJob job;

    @BeforeAll
    static void initializeMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "compensation-job-test"),
                SeckillStockCompensationOutbox.class
        );
    }

    @BeforeEach
    void setUp() {
        mapper = mock(SeckillStockCompensationOutboxMapper.class);
        publisher = mock(SeckillOrderClosedPublisher.class);
        job = new SeckillStockCompensationOutboxJob(mapper, publisher);
    }

    @Test
    void marksPublishedOnlyAfterConfirmationCompletes() throws Exception {
        SeckillStockCompensationOutbox record = record(1001L, "request-001");
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(record), List.of(record));
        CompletableFuture<Void> confirmation = new CompletableFuture<>();
        CountDownLatch sending = new CountDownLatch(1);
        when(publisher.send(any(SeckillOrderClosedMessage.class))).thenAnswer(invocation -> {
            sending.countDown();
            return confirmation;
        });

        CompletableFuture<Void> task = CompletableFuture.runAsync(job::dispatchPending);
        try {
            assertTrue(sending.await(5, TimeUnit.SECONDS));
            verify(mapper, never()).update(
                    nullable(SeckillStockCompensationOutbox.class), any(Wrapper.class)
            );
            confirmation.complete(null);
            task.get(5, TimeUnit.SECONDS);
        } finally {
            confirmation.complete(null);
        }

        verify(publisher).send(new SeckillOrderClosedMessage(
                record.getRequestId(), record.getOrderId(), record.getVoucherId(),
                record.getUserId(), record.getFailureCode()
        ));
        assertEquals(record.getId(), publishedRecordId());
    }

    @Test
    void failedPublishDoesNotMarkThatRecordAndDoesNotBlockNextRecord() {
        SeckillStockCompensationOutbox first = record(1001L, "request-001");
        SeckillStockCompensationOutbox second = record(1002L, "request-002");
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(second), List.of(first, second));
        when(publisher.send(any(SeckillOrderClosedMessage.class))).thenAnswer(invocation -> {
            SeckillOrderClosedMessage message = invocation.getArgument(0);
            return "request-001".equals(message.requestId())
                    ? CompletableFuture.failedFuture(new IllegalStateException("MQ unavailable"))
                    : CompletableFuture.completedFuture(null);
        });

        job.dispatchPending();

        verify(publisher, times(2)).send(any(SeckillOrderClosedMessage.class));
        assertEquals(second.getId(), publishedRecordId());
    }

    @Test
    void revisitsFailedRecordInNextCycleEvenWhenNewRecordsArrive() {
        SeckillStockCompensationOutbox first = record(1001L, "request-001");
        SeckillStockCompensationOutbox second = record(1002L, "request-002");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(
                List.of(first), List.of(first),
                List.of(second), List.of(first, second)
        );
        when(publisher.send(any(SeckillOrderClosedMessage.class))).thenReturn(
                CompletableFuture.failedFuture(new IllegalStateException("temporary failure")),
                CompletableFuture.completedFuture(null),
                CompletableFuture.completedFuture(null)
        );

        job.dispatchPending();
        verify(mapper, never()).update(
                nullable(SeckillStockCompensationOutbox.class), any(Wrapper.class)
        );
        job.dispatchPending();

        ArgumentCaptor<SeckillOrderClosedMessage> messages =
                ArgumentCaptor.forClass(SeckillOrderClosedMessage.class);
        verify(publisher, times(3)).send(messages.capture());
        assertEquals(List.of("request-001", "request-001", "request-002"),
                messages.getAllValues().stream()
                        .map(SeckillOrderClosedMessage::requestId).toList());
        verify(mapper, times(2)).update(
                nullable(SeckillStockCompensationOutbox.class), any(Wrapper.class)
        );
    }

    @Test
    void invalidBusinessRecordIsNotPublishedOrMarkedSent() {
        SeckillStockCompensationOutbox invalid = record(1001L, "request-001");
        invalid.setFailureCode(null);
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(invalid), List.of(invalid));

        job.dispatchPending();

        verifyNoInteractions(publisher);
        verify(mapper, never()).update(
                nullable(SeckillStockCompensationOutbox.class), any(Wrapper.class)
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Long publishedRecordId() {
        ArgumentCaptor<Wrapper> updates = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).update(nullable(SeckillStockCompensationOutbox.class), updates.capture());
        LambdaUpdateWrapper<SeckillStockCompensationOutbox> update =
                (LambdaUpdateWrapper<SeckillStockCompensationOutbox>) updates.getValue();
        update.getSqlSegment();
        return update.getParamNameValuePairs().values().stream()
                .filter(value -> value instanceof Long)
                .map(value -> (Long) value)
                .findFirst().orElseThrow();
    }

    private SeckillStockCompensationOutbox record(Long id, String requestId) {
        SeckillStockCompensationOutbox record = new SeckillStockCompensationOutbox();
        record.setId(id);
        record.setRequestId(requestId);
        record.setOrderId(id + 10000);
        record.setVoucherId(10L);
        record.setUserId(id + 20000);
        record.setFailureCode("STOCK_UNAVAILABLE");
        record.setStatus(0);
        return record;
    }
}
