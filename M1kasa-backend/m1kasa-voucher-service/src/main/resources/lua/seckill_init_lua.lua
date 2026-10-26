-- KEYS[1]：活动信息的 Redis Key
-- ARGV[1]：初始库存
-- ARGV[2]：开始时间，毫秒时间戳
-- ARGV[3]：结束时间，毫秒时间戳

-- 已初始化过，就不覆盖现有库存
if redis.call('EXISTS', KEYS[1]) == 1 then
    return 0
end

-- 一次写入完整活动信息
redis.call(
    'HSET', KEYS[1],
    'stock', ARGV[1],
    'beginTime', ARGV[2],
    'endTime', ARGV[3]
)

return 1
