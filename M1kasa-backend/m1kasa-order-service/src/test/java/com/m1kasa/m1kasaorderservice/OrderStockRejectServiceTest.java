package com.m1kasa.m1kasaorderservice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.m1kasa.m1kasaorderservice.constant.OrderStatus;
import com.m1kasa.m1kasaorderservice.entity.SeckillStockCompensationOutbox;
import com.m1kasa.m1kasaorderservice.mapper.SeckillStockCompensationOutboxMapper;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.m1kasaorderservice.service.OrderStockRejectService;
import com.m1kasa.m1kasaorderservice.service.impl.OrderStockRejectServiceImpl;
import com.m1kasa.m1kasaorderservice.statemachine.OrderStateMachine;
import com.m1kasa.voucher.api.message.SeckillStockRejectedMessage;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用独立 H2 数据库、真实 MyBatis Mapper 和 Spring 事务代理；
 * 不启动业务应用，不连接本机 MySQL、Redis 或 RabbitMQ。
 */
class OrderStockRejectServiceTest {

    private static final SeckillStockRejectedMessage MESSAGE =
            new SeckillStockRejectedMessage(
                    "request-001", 10001L, 10L, 1011L, "STOCK_UNAVAILABLE"
            );

    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private OrderStockRejectService service;
    private SeckillStockCompensationOutboxMapper outboxMapper;

    @BeforeEach
    void setUp() {
        String database = "compensation_" + UUID.randomUUID().toString().replace("-", "");
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + database
                        + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
                        + ";LOCK_TIMEOUT=5000"
                        + ";INIT=CREATE SCHEMA IF NOT EXISTS m1kasa_order\\;SET SCHEMA m1kasa_order",
                "sa",
                ""
        );
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE tb_voucher_order (
                    id BIGINT PRIMARY KEY,
                    request_id VARCHAR(64) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    voucher_id BIGINT NOT NULL,
                    status INTEGER NOT NULL,
                    failure_code VARCHAR(64),
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (user_id, voucher_id)
                )
                """);
        new ResourceDatabasePopulator(
                new ClassPathResource("db/stock-compensation-outbox.sql")
        ).execute(dataSource);
        jdbc.update("""
                INSERT INTO tb_voucher_order
                    (id, request_id, user_id, voucher_id, status)
                VALUES (?, ?, ?, ?, ?)
                """, MESSAGE.orderId(), MESSAGE.requestId(), MESSAGE.userId(),
                MESSAGE.voucherId(), OrderStatus.WAITING_STOCK);

        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> dataSource);
        context.register(TestConfiguration.class);
        context.refresh();
        service = context.getBean(OrderStockRejectService.class);
        outboxMapper = context.getBean(SeckillStockCompensationOutboxMapper.class);
    }

    @AfterEach
    void tearDown() {
        try {
            if (jdbc != null) {
                jdbc.execute("SHUTDOWN");
            }
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    @Test
    void closesOrderAndSavesPendingCompensationInOneCall() {
        service.reject(MESSAGE);

        assertEquals(OrderStatus.CANCELLED, orderStatus());
        assertEquals(MESSAGE.failureCode(), orderFailureCode());
        assertEquals(1, outboxCount());
        SeckillStockCompensationOutbox record = onlyOutbox();
        assertEquals(MESSAGE.requestId(), record.getRequestId());
        assertEquals(MESSAGE.orderId(), record.getOrderId());
        assertEquals(MESSAGE.voucherId(), record.getVoucherId());
        assertEquals(MESSAGE.userId(), record.getUserId());
        assertEquals(MESSAGE.failureCode(), record.getFailureCode());
        assertEquals(0, record.getStatus());
        assertNotNull(record.getCreateTime());
        assertNotNull(record.getUpdateTime());
    }

    @Test
    void rollsBackCancellationWhenCompensationInsertFails() {
        // 请求 ID 被另一笔通知占用，真实唯一约束令后续 INSERT 失败。
        insertOutbox("request-001", 10002L, 10L, 1012L, "STOCK_UNAVAILABLE", 0);

        assertThrows(DuplicateKeyException.class, () -> service.reject(MESSAGE));

        assertEquals(OrderStatus.WAITING_STOCK, orderStatus());
        assertNull(orderFailureCode());
        assertEquals(1, outboxCount());
        assertEquals(10002L, onlyOutbox().getOrderId());
    }

    @Test
    void duplicateMessageKeepsOneCompensationRecord() {
        service.reject(MESSAGE);
        Long firstId = onlyOutbox().getId();

        service.reject(MESSAGE);

        assertEquals(1, outboxCount());
        assertEquals(firstId, onlyOutbox().getId());
        assertEquals(OrderStatus.CANCELLED, orderStatus());
    }

    @Test
    void duplicateMessageDoesNotResetPublishedNotification() {
        service.reject(MESSAGE);
        Long firstId = onlyOutbox().getId();
        jdbc.update("UPDATE tb_seckill_stock_compensation_outbox SET status = 1");

        service.reject(MESSAGE);

        assertEquals(1, outboxCount());
        assertEquals(firstId, onlyOutbox().getId());
        assertEquals(1, onlyOutbox().getStatus());
    }

    @Test
    void backfillsNotificationForAnAlreadyClosedMatchingOrder() {
        jdbc.update("""
                UPDATE tb_voucher_order SET status = ?, failure_code = ?
                """, OrderStatus.CANCELLED, MESSAGE.failureCode());

        service.reject(MESSAGE);

        assertEquals(1, outboxCount());
        assertEquals(0, onlyOutbox().getStatus());
        assertEquals(OrderStatus.CANCELLED, orderStatus());
    }

    @Test
    void rejectsMessageWhoseIdentityDoesNotMatchOrder() {
        SeckillStockRejectedMessage wrongUser = new SeckillStockRejectedMessage(
                MESSAGE.requestId(), MESSAGE.orderId(), MESSAGE.voucherId(),
                9999L, MESSAGE.failureCode()
        );

        assertThrows(IllegalStateException.class, () -> service.reject(wrongUser));
        assertEquals(OrderStatus.WAITING_STOCK, orderStatus());
        assertEquals(0, outboxCount());
    }

    @Test
    void doesNotCloseWaitingPaymentOrPaidOrders() {
        for (int status : new int[]{OrderStatus.WAITING_PAYMENT, OrderStatus.PAID}) {
            jdbc.update("UPDATE tb_voucher_order SET status = ?", status);

            assertThrows(IllegalStateException.class, () -> service.reject(MESSAGE));

            assertEquals(status, orderStatus());
            assertEquals(0, outboxCount());
        }
    }

    @Test
    void rejectsACompensationRecordWithConflictingBusinessData() {
        jdbc.update("""
                UPDATE tb_voucher_order SET status = ?, failure_code = ?
                """, OrderStatus.CANCELLED, MESSAGE.failureCode());
        insertOutbox("other-request", MESSAGE.orderId(), 99L,
                MESSAGE.userId(), MESSAGE.failureCode(), 0);

        assertThrows(IllegalStateException.class, () -> service.reject(MESSAGE));

        assertEquals(1, outboxCount());
        assertEquals("other-request", onlyOutbox().getRequestId());
    }

    @Test
    void doesNotBackfillForAnOrderClosedForAnotherReason() {
        jdbc.update("""
                UPDATE tb_voucher_order SET status = ?, failure_code = ?
                """, OrderStatus.CANCELLED, "OTHER_REASON");

        assertThrows(IllegalStateException.class, () -> service.reject(MESSAGE));

        assertEquals(0, outboxCount());
        assertEquals("OTHER_REASON", orderFailureCode());
    }

    @Test
    void concurrentDuplicateMessagesCreateOneCompensation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                start.await();
                service.reject(MESSAGE);
                return null;
            });
            var second = executor.submit(() -> {
                start.await();
                service.reject(MESSAGE);
                return null;
            });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);

            assertEquals(OrderStatus.CANCELLED, orderStatus());
            assertEquals(1, outboxCount());
        } finally {
            executor.shutdownNow();
        }
    }

    private int orderStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM tb_voucher_order WHERE id = ?",
                Integer.class, MESSAGE.orderId()
        );
    }

    private String orderFailureCode() {
        return jdbc.queryForObject(
                "SELECT failure_code FROM tb_voucher_order WHERE id = ?",
                String.class, MESSAGE.orderId()
        );
    }

    private int outboxCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_seckill_stock_compensation_outbox",
                Integer.class
        );
    }

    private SeckillStockCompensationOutbox onlyOutbox() {
        return outboxMapper.selectList(null).get(0);
    }

    private void insertOutbox(
            String requestId, Long orderId, Long voucherId,
            Long userId, String failureCode, int status
    ) {
        SeckillStockCompensationOutbox record = new SeckillStockCompensationOutbox();
        record.setRequestId(requestId);
        record.setOrderId(orderId);
        record.setVoucherId(voucherId);
        record.setUserId(userId);
        record.setFailureCode(failureCode);
        record.setStatus(status);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        outboxMapper.insert(record);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @MapperScan(basePackageClasses = VoucherOrderMapper.class)
    static class TestConfiguration {

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        OrderStateMachine orderStateMachine() {
            return new OrderStateMachine();
        }

        @Bean
        OrderStockRejectService orderStockRejectService(
                VoucherOrderMapper orderMapper,
                OrderStateMachine stateMachine,
                SeckillStockCompensationOutboxMapper compensationMapper
        ) {
            return new OrderStockRejectServiceImpl(
                    orderMapper, stateMachine, compensationMapper
            );
        }
    }
}
