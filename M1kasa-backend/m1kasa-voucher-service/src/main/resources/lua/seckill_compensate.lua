-- KEYS[1]：活动信息 Hash
-- KEYS[2]：本次请求的预占凭证 Hash
--
-- ARGV[1]：原抢券请求的 requestId
-- ARGV[2]：用户 ID
--
-- 返回值：
-- 0：补偿成功
-- 1：此前已经补偿，本次不重复处理
-- 2：预占凭证不存在
-- 3：凭证与消息中的请求或用户不匹配
-- 4：凭证状态不支持补偿
-- 5：参数、Redis 类型或库存数据异常

local activityKey = KEYS[1]
local reservationKey = KEYS[2]

local requestId = ARGV[1]
local userId = ARGV[2]

-- 1. 检查调用参数
if not requestId or requestId == ''
    or not userId or userId == '' then
    return 5
end

-- 2. 必须存在原请求的预占凭证
local reservationType =
    redis.call('TYPE', reservationKey).ok

if reservationType == 'none' then
    return 2
end

if reservationType ~= 'hash' then
    return 5
end

-- 3. 读取凭证中的身份和状态
local reservation = redis.call(
    'HMGET',
    reservationKey,
    'requestId',
    'userId',
    'state'
)

local savedRequestId = reservation[1]
local savedUserId = reservation[2]
local state = reservation[3]

-- 4. 只能补偿这条消息对应的那次预占
if savedRequestId ~= requestId
    or savedUserId ~= userId then
    return 3
end

-- 5. 重复消息直接返回，不再增加库存
if state == 'COMPENSATED' then
    return 1
end

-- 6. 只有预占成功的凭证才能进行补偿
if state ~= 'RESERVED' then
    return 4
end

-- 7. 检查活动数据，不能凭空创建库存
if redis.call('TYPE', activityKey).ok ~= 'hash' then
    return 5
end

local stockText =
    redis.call('HGET', activityKey, 'stock')

local stock = tonumber(stockText or '')

-- 项目库存使用 Integer：
-- 必须为非负整数字符串，回补后也不能超出范围
if not stock
    or stock < 0
    or stock >= 2147483647
    or stock ~= math.floor(stock)
    or stockText ~= string.format('%.0f', stock) then
    return 5
end

-- 上面的检查全部通过后，再执行修改

-- 8. 暂停异常活动，阻止新的抢券请求
redis.call(
    'HSET', activityKey,
    'paused', '1'
)

-- 9. 归还这次请求预扣的 1 份 Redis 库存
redis.call(
    'HINCRBY', activityKey,
    'stock', 1
)

-- 10. 标记这次预占已经补偿
redis.call(
    'HSET', reservationKey,
    'state', 'COMPENSATED'
)

return 0
