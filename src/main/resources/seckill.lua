-- 1.参数列表
-- 1.1优惠券id
local voucherId = ARGV[1]
-- 1.2用户id
local userId = ARGV[2]

-- 2.数据key
-- 2.1库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 2.2订单key
local orderKey = 'seckill:order:' .. voucherId

-- 3.脚本业务
-- 3.1判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.1.2库存不足，返回1
    return 1
end
-- 3.2判断用户是否重复下单 sismember orderKey userId
if(tonumber(redis.call('sismember', orderKey, userId)) == 1) then
    -- 3.2.3存在，说明重复下单，返回2
    return 2
end
-- 3.3扣库存 incrby stockKey -1
redis.call('incrby', stockKey, -1)
--3.4下单(保存用户) sadd orderKey userId
redis.call('sadd', orderKey, userId)

-- 修改：生产消息到kafka
---- 3.5 发送消息到消息队列 XADD
--redis.call('xadd', 'stream.order', '*', 'userId',userId, 'voucherId',voucherId, 'id',orderId)

return 0