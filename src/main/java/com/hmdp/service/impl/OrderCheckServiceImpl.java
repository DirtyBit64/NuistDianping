package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.constant.KafkaConstants;
import com.hmdp.constant.OrderConstants;
import com.hmdp.constant.RedisConstants;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.OrderCheckService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;

@Service
@Slf4j
public class OrderCheckServiceImpl implements OrderCheckService {
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 类加载时初始化lua脚本执行对象
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource(SystemConstants.SECKILL_LUA));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /** 单独的服务，与入库服务分离
     * 秒杀逻辑优化——Kafka消息队列
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVourcher(Long voucherId) {
        // 1.执行秒杀资格判断seckill.lua
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId(RedisConstants.ORDER_ID_KEY_PREFIX);
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        // 2.判断执行结果是否为0
        int r = result.intValue();
        if(r != 0){
            // 2.1不为0没有购买资格
            return Result.fail(r == 1 ? OrderConstants.OUT_OF_STOCK : OrderConstants.REPEAT_ORDER);
        }

        // 2.2下单成功-->生成订单信息
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        // 2.3 订单信息转为Json
        String jsonStr = JSONUtil.toJsonStr(JSONUtil.toJsonStr(voucherOrder));
        log.info("生成后台订单信息到kafka：{}", jsonStr);
        // 2.4 发送到mq
        kafkaTemplate.send(KafkaConstants.VOUCHER_ORDER_TOPIC, String.valueOf(voucherId), jsonStr);

        // 3.用户有购买资格，响应给用户结果；mq异步执行下单业务
        return Result.ok(orderId);
    }
}
