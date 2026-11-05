-- 在订单库执行一次。本项目尚未配置自动数据库迁移，本文件不会在启动时自动执行。
-- 若此前已按相同结构建表，CREATE TABLE IF NOT EXISTS 会保留现有表和数据。
CREATE TABLE IF NOT EXISTS m1kasa_order.tb_seckill_stock_compensation_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL COMMENT '原抢券请求ID',
    order_id BIGINT NOT NULL COMMENT '已关闭的订单ID',
    voucher_id BIGINT NOT NULL COMMENT '优惠券ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    failure_code VARCHAR(64) NOT NULL COMMENT '失败原因',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0待发送，1已通过发布检查',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_compensation_request (request_id),
    UNIQUE KEY uk_compensation_order (order_id),
    KEY idx_compensation_status_id (status, id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COMMENT = '秒杀订单失败后的库存补偿通知';
