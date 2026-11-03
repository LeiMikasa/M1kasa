-- 当前用户的关注列表缓存
local followingKey = KEYS[1]

-- 初始化完成标记
local readyMember = ARGV[1]

-- 缓存有效期，单位毫秒
local ttlMillis = tonumber(ARGV[2])

-- 检查参数
if not readyMember or readyMember == ''
        or not ttlMillis or ttlMillis <= 0
        or ttlMillis ~= math.floor(ttlMillis) then
    return -2
end

-- 缓存只能不存在，或者是 Set
local keyType = redis.call('TYPE', followingKey).ok

if keyType ~= 'none' and keyType ~= 'set' then
    return -1
end

-- 已经完整初始化，直接返回
if redis.call('SISMEMBER', followingKey, readyMember) == 1 then
    return 0
end

-- 清理没有完成标记的残留数据
redis.call('DEL', followingKey)

-- ARGV[3] 开始是数据库查询出的全部被关注者 ID
for i = 3, #ARGV do
    redis.call('SADD', followingKey, ARGV[i])
end

-- 所有关注关系写入后，加入初始化完成标记
redis.call('SADD', followingKey, readyMember)

-- 整份缓存设置过期时间
redis.call('PEXPIRE', followingKey, ttlMillis)

return 1
