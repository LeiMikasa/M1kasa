package com.m1kasa.m1kasaorderservice;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.m1kasa.common.base.context.UserContext;
import com.m1kasa.m1kasaorderservice.controller.OrderQueryController;
import com.m1kasa.m1kasaorderservice.entity.VoucherOrder;
import com.m1kasa.common.auth.interceptor.LoginInterceptor;
import com.m1kasa.m1kasaorderservice.mapper.VoucherOrderMapper;
import com.m1kasa.m1kasaorderservice.service.impl.OrderQueryServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 不启动应用上下文，不访问真实数据库、Redis 或 RabbitMQ。 */
class OrderQueryControllerTest {

    private static final long LARGE_ORDER_ID = 9007199254740993L;
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    private VoucherOrderMapper orderMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> tokens;
    private MockMvc mvc;

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "order-query-test"),
                VoucherOrder.class
        );
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        UserContext.clear();
        orderMapper = mock(VoucherOrderMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        tokens = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(tokens);
        when(tokens.get("m1kasa:login:token:user-a")).thenReturn("2474");
        when(tokens.get("m1kasa:login:token:user-b")).thenReturn("2475");
        when(tokens.get("m1kasa:login:token:user-c")).thenReturn("2476");

        when(orderMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            LambdaQueryWrapper<VoucherOrder> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("user_id"), "订单查询必须限定当前用户");
            assertTrue(sql.contains("voucher_id"), "订单查询必须限定优惠券");
            Map<String, Object> parameters = query.getParamNameValuePairs();
            assertTrue(parameters.containsValue(10L));
            if (parameters.containsValue(2474L)) {
                return order(LARGE_ORDER_ID, 2474L);
            }
            if (parameters.containsValue(2475L)) {
                return order(LARGE_ORDER_ID + 1, 2475L);
            }
            assertTrue(parameters.containsValue(2476L));
            return null;
        });

        mvc = MockMvcBuilders.standaloneSetup(
                        new OrderQueryController(new OrderQueryServiceImpl(orderMapper))
                )
                .addInterceptors(new LoginInterceptor(redisTemplate))
                .build();
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void rejectsUnauthenticatedRequestEvenWithForgedUserHeader() throws Exception {
        mvc.perform(get("/order/of/voucher/10").header("X-User-Id", "2474"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderMapper, redisTemplate);
        assertNull(UserContext.getUserId());
    }

    @Test
    void rejectsExpiredTokenWithoutQueryingOrders() throws Exception {
        mvc.perform(get("/order/of/voucher/10")
                        .header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(orderMapper);
        assertNull(UserContext.getUserId());
    }

    @Test
    void serializesIdsAsStringsWithoutLosingPrecision() throws Exception {
        JsonNode body = body(mvc.perform(get("/order/of/voucher/10")
                        .header("Authorization", "Bearer user-a"))
                .andExpect(status().isOk()).andReturn());

        assertTrue(body.get("success").asBoolean());
        assertTrue(body.get("data").get("id").isString());
        assertEquals(Long.toString(LARGE_ORDER_ID), body.get("data").get("id").asString());
        assertEquals("10", body.get("data").get("voucherId").asString());
        assertEquals(1, body.get("data").get("status").asInt());
        assertNotNull(body.get("data").get("createTime"));
        assertNull(UserContext.getUserId());
    }

    @Test
    void isolatesOrdersByRedisAuthenticatedUser() throws Exception {
        JsonNode userA = body(mvc.perform(get("/order/of/voucher/10")
                        .header("Authorization", "Bearer user-a"))
                .andExpect(status().isOk()).andReturn());
        JsonNode userB = body(mvc.perform(get("/order/of/voucher/10")
                        .header("Authorization", "Bearer user-b"))
                .andExpect(status().isOk()).andReturn());

        assertEquals(Long.toString(LARGE_ORDER_ID), userA.get("data").get("id").asString());
        assertEquals(Long.toString(LARGE_ORDER_ID + 1), userB.get("data").get("id").asString());
        assertNull(UserContext.getUserId());
    }

    @Test
    void ignoresClientUserIdHeaderAndQueryParameter() throws Exception {
        JsonNode body = body(mvc.perform(get("/order/of/voucher/10")
                        .param("userId", "2475")
                        .header("X-User-Id", "2475")
                        .header("Authorization", "Bearer user-a"))
                .andExpect(status().isOk()).andReturn());

        assertEquals(Long.toString(LARGE_ORDER_ID), body.get("data").get("id").asString());
    }

    @Test
    void returnsSuccessWithNullDataWhenOrderHasNotBeenCreated() throws Exception {
        JsonNode body = body(mvc.perform(get("/order/of/voucher/10")
                        .header("Authorization", "Bearer user-c"))
                .andExpect(status().isOk()).andReturn());

        assertTrue(body.get("success").asBoolean());
        assertTrue(body.get("data").isNull());
        assertNull(UserContext.getUserId());
    }

    @Test
    void rejectsInvalidVoucherBeforeQueryingDatabase() throws Exception {
        mvc.perform(get("/order/of/voucher/0")
                        .header("Authorization", "Bearer user-a"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orderMapper);
        assertNull(UserContext.getUserId());
    }

    private JsonNode body(MvcResult result) {
        return jsonMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private VoucherOrder order(long id, long userId) {
        VoucherOrder order = new VoucherOrder();
        order.setId(id);
        order.setUserId(userId);
        order.setVoucherId(10L);
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.of(2026, 10, 26, 12, 0));
        return order;
    }
}
