-- 抢劵脚本
-- KEYS[1]: 活动信息Hash
-- KEYS[2]: 已获得资格的用户 Set
-- ARGV[1]: 当前用户ID
-- KEYS[3]：待发送记录 Hash
-- ARGV[2]：本次抢券请求 ID

-- 返回值:
-- 0:获得资格
-- 1:库存不足
-- 2:重复领取
-- 3:活动尚未开始
-- 4:活动已经结束
-- 5:活动未初始化或数据不完整

local activityKey = KEYS[1]
local buyersKey = KEYS[2]
local userId = ARGV[1]

local outboxKey = KEYS[3]
local requestId = ARGV[2]

if not requestId or requestId == '' then
    return 5
end

-- 提前检查待发送记录的类型，避免写入时发生 WRONGTYPE
local outboxType = redis.call('TYPE', outboxKey).ok

if outboxType ~= 'none' and outboxType ~= 'hash' then
    return 5
end
-- 1.读取活动消息
local activity = redis.call(
    'HMGET', activityKey,
    'stock', 'beginTime', 'endTime'
)
local stock = tonumber(activity[1] or '')
local beginTime = tonumber(activity[2] or '')
local endTime = tonumber(activity[3] or '')

if not stock or not beginTime or not endTime then
    return 5
end

-- 2.使用redis的时间，单位转换为毫秒
local redisTime = redis.call('TIME')
local now = tonumber(redisTime[1]) * 1000
    + math.floor(tonumber(redisTime[2]) / 1000)
if now < beginTime then
    return 3
end

if now >= endTime then
    return 4
end

-- 3.判断用户是否已经获得资格
if redis.call('SISMEMBER', buyersKey, userId) == 1 then
    return 2
end

-- 4.判断库存
if stock <= 0 then
    return 1
end

-- 5. 扣减库存，并记录用户
-- 扣减库存
redis.call('HINCRBY', activityKey, 'stock', -1)

-- 记录已经获得资格的用户
redis.call('SADD', buyersKey, userId)

-- 保存待发送的下单消息记录
redis.call('HSET', outboxKey, requestId, userId)

return 0
