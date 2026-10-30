-- KEYS[1]: 点赞状态Hash
-- KEYS[2]: 点赞总数String
-- KEYS[3]: 待发送记录Hash

--ARGV[1]:用户ID
--ARGV[2]:数据库中的点赞总数
--ARGV[3]:数据库中的用户状态，0或1
--ARGV[4]:数据库中的用户状态版本
--ARGV[5]:数据库中的更新时间，毫秒时间戳

local stateKey = KEYS[1]
local countKey = KEYS[2]
local outboxKey = KEYS[3]

local userId = ARGV[1]
local initialCount = ARGV[2]
local initialLiked = ARGV[3]
local initialVersion = ARGV[4]
local initialTime = ARGV[5]

-- 判断是否为非负整数字符串
local function isUnsignedInteger(value)
    return value and string.match(value, '^%d+$') ~= nil
end

-- 1. 校验参数，参数不合法时不修改 Redis
if not isUnsignedInteger(userId) or tonumber(userId) <= 0 then
    return -1
end

if not isUnsignedInteger(initialCount)
    or not isUnsignedInteger(initialVersion)
    or not isUnsignedInteger(initialTime) then
    return -1
end

if initialLiked ~= '0' and initialLiked ~= '1' then
    return -1
end

-- 2. 检查 Key 类型，避免写到一半遇到 WRONGTYPE
local stateType = redis.call('TYPE', stateKey).ok
local countType = redis.call('TYPE', countKey).ok
local outboxType = redis.call('TYPE', outboxKey).ok

if stateType ~= 'none' and stateType ~= 'hash' then
    return -3
end

if countType ~= 'none' and countType ~= 'string' then
    return -3
end

if outboxType ~= 'none' and outboxType ~= 'hash' then
    return -3
end
local stateExists = stateType ~= 'none'
local countExists = countType ~= 'none'

-- 3. 状态和计数只剩一部分，不能直接使用旧数据库值覆盖
if stateExists ~= countExists then
    return -2
end

-- 有待发送数据，但状态和计数丢失，需要单独恢复
if not countExists and outboxType ~= 'none' then
    return -2
end

-- 4. 当前用户已经初始化，保留 Redis 中的最新状态
if countExists
        and redis.call('HEXISTS', stateKey, userId) == 1 then
    return 0
end

-- 5. 只有首次初始化这篇笔记时，才装入数据库计数
if not countExists then
    redis.call('SET', countKey, initialCount)
end

-- 6. 只初始化当前用户，不覆盖已有用户状态
local initialState =
        initialLiked .. ':' .. initialVersion .. ':' .. initialTime

redis.call('HSETNX', stateKey, userId, initialState)

return 1
