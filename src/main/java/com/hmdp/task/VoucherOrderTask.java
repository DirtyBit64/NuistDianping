package com.hmdp.task;

import com.hmdp.constant.OrderConstants;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 定时任务类,定时处理订单状态
 */
@Component
@Slf4j
public class VoucherOrderTask {

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 处理超时订单
     * 每5分钟触发一次
     */
    @Scheduled(cron = "0 0/5 * * * ? * ")
    @Transactional
    @SuppressWarnings("No Check")
    public void processTimeoutOrder(){
        log.info("定时处理超时订单：{}", LocalDateTime.now());
        // 1. 查询超时未支付订单
        List<VoucherOrder> orders = voucherOrderMapper.getByStatusAndOrderTimeLT(OrderConstants.NOPAY_STATUS,
                LocalDateTime.now().minusMinutes(OrderConstants.DELAY_MINUTE));
        // 2. 将这些订单设置为已取消状态
        List<Long> voucherIdList = new ArrayList<>();
        for (VoucherOrder order : orders) {
            order.setStatus(OrderConstants.CANCEL_STATUS);
            voucherIdList.add(order.getVoucherId());
        }
        voucherOrderMapper.updateBatch(orders);
        // 3. 更新db秒杀优惠券库存
        seckillVoucherMapper.updateStock(voucherIdList);
        // 4. TODO pipeline/lua批量还原redis秒杀优惠券库存
    }

}
