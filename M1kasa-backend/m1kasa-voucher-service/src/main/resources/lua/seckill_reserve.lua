-- 抢劵脚本
-- KEYS[1]: 活动信息Hash
-- KEYS[2]: 已获得资格的用户 Set
-- ARGV[1]: 当前用户ID

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
redis.call('HINCRBY', activityKey, 'stock', -1)
redis.call('SADD', buyersKey, userId)

return 0
