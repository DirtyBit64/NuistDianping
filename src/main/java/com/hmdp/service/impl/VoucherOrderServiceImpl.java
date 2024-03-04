package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀下单
     * @param voucherId
     * @return
     */
    @Transactional
    public Result seckillVourcher(Long voucherId) {
        // 1.查询优惠卷
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        // 3.判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }
        // 4.判断库存是否充足
        if(voucher.getStock() < 1){
            return Result.fail("库存不足");
        }

        // 5.一人一单---Solved 重复下单问题
        Long userId = UserHolder.getUser().getId();
        // 若采用方法内锁代码块的方式，有可能出现锁释放但spring管理的事务未提交的情况
        // 创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 获取锁
        boolean isLock = lock.tryLock(5L);
        if(!isLock){
            // 获取锁失败
            return Result.fail("不允许重复下单！");
        }
        // 获取代理对象（事务）
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            // 释放锁
            lock.unlock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        // 5.一人一单---Solve 重复下单问题
        Long userId = UserHolder.getUser().getId();
        // 为每个用户加一把锁
        // 5.1查询订单
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        // 5.2订单是否存在
        if(count > 0){
            return Result.fail("您已经购买过了！");
        }

        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 乐观锁解决超卖问题
                .update();
        if(!success){
            // 扣减失败
            return Result.fail("库存不足");
        }

        // 6.1创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1.1订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.1.2用户id
        voucherOrder.setUserId(userId);
        // 6.1.3代金卷id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok();
    }
}
