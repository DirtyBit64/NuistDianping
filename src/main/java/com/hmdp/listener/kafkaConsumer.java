package com.hmdp.listener;

import cn.hutool.json.JSONUtil;
import com.hmdp.constant.KafkaConstants;
import com.hmdp.constant.RedisConstants;
import com.hmdp.entity.Shop;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IShopService;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

@Component
@Slf4j
public class kafkaConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private IShopService shopService;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 优惠券秒杀订单异步入库
     * @param record 消息
     */
    @KafkaListener(topics = KafkaConstants.VOUCHER_ORDER_TOPIC, groupId = KafkaConstants.ORDER_GROUP)
    public void handlerVoucherOrder(ConsumerRecord<String, String> record){
        // 1.判断消息获取是否成功
        String msg = record.value();
        if(StringUtils.isEmpty(msg)){
            return;
        }
        // 2. 解析json
        VoucherOrder voucherOrder = JSONUtil.toBean(msg, VoucherOrder.class);
        // 这里默认支付成功
        voucherOrder.setStatus(2);
        // 3.获取成功，创建锁对象
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_ORDER_KEY + voucherOrder.getUserId());
        // 3.1获取锁
        try {
            boolean isLock = lock.tryLock();
            if(!isLock){
                // 3.2获取锁失败，返回
                log.error("获取锁失败..");
                return;
            }
            // 4.订单入库
            voucherOrderService.createVoucherOrder(voucherOrder);
            // 如果订单创建失败抛出异常，那么捕捉到进行多次重试
        }finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 处理商铺信息变更消息
     * @param record 消息
     */
    @KafkaListener(topics = KafkaConstants.VOUCHER_ORDER_TOPIC, groupId = KafkaConstants.SHOP_GROUP)
    public void handlerCacheUpdate(ConsumerRecord<String, String> record){
        // 1.判断消息获取是否成功
        String msg = record.value();
        if(StringUtils.isEmpty(msg)){
            return;
        }
        // 2. 解析json
        Shop shop = JSONUtil.toBean(msg, Shop.class);
        // 3. 更新数据库及各级缓存
        shopService.update(shop);
    }

}
