-- KEYS[1]：点赞 Outbox Hash
-- ARGV[1]：用户 ID，也就是 Hash 字段
-- ARGV[2]：本次已经发送并获得确认的原始记录

local outboxKey = KEYS[1]
local userId = ARGV[1]
local expectedState = ARGV[2]

-- 读取现在的待发送记录
local currentState = redis.call('HGET', outboxKey, userId)

-- 只有记录仍然相同，才删除
if currentState == expectedState then
    return redis.call('HDEL', outboxKey, userId)
end

-- 已被其他任务删除，或者已经产生了更新版本
return 0
