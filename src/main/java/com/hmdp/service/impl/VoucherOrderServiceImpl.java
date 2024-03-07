package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * <p>
 *  优惠券秒杀业务实现
 * </p>
 *
 * @author DirtyBit
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

    @Resource
    private RedissonClient redissonClient;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandle());
    }

    /**
     * 定义线程方法，从stream消息队列中获取下单消息
     */
    private class VoucherOrderHandle implements Runnable{
        String queueName = "stream.order";
        @Override
        public void run() {
            while(true){
                try {
                    // 1.获取消息队列中的订单信息
                    // xreadgroup group g1 c1 count 1 block 2000 stream.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    // 2.判断消息获取是否成功
                    if(list == null || list.isEmpty()){
                        // 2.1获取失败说明没有消息
                        continue;
                    }
                    // 3.解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    // 3. 获取成功，创建订单
                    handleVoucherOrder(voucherOrder);
                    // 4. ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(
                            queueName,
                            "g1",
                            record.getId()
                    );
                } catch (Exception e) {
                    // 5.没确认，去pending-list中取
                    handlePendingList();
                }
            }
        }

        /**
         * 从pendinglist中取消息
         */
        private void handlePendingList() {
            while(true){
                try {
                    // 1.获取消息队列pending-list中的订单信息
                    // xreadgroup group g1 c1 count 1 block 2000 stream.order >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    // 2.判断消息获取是否成功
                    if(list == null || list.isEmpty()){
                        // 2.1获取失败说明pending-list没有消息,结束循环
                        break;
                    }
                    // 3.解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), false);
                    // 3. 获取成功，创建订单
                    handleVoucherOrder(voucherOrder);
                    // 4. ACK确认——异常
                    stringRedisTemplate.opsForStream().acknowledge(
                            queueName,
                            "g1",
                            record.getId()
                    );

                } catch (Exception e) {
                    // 5.没确认，去pending-list中取
                    log.error("处理pending-list异常", e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }

    // 类加载时初始化lua脚本执行对象
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 定义一个阻塞队列
    // private BlockingQueue<VoucherOrder> ordersTasks = new ArrayBlockingQueue<>(1024);

    private IVoucherOrderService proxy;

    /**
     * 秒杀逻辑优化——Redis的stream消息队列
     * @param voucherId
     * @return
     */
    public Result seckillVourcher(Long voucherId) {
        // 1.执行秒杀资格判断seckill.lua
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        // 2.判断执行结果是否为0
        int r = result.intValue();
        if(r != 0){
            // 2.1不为0没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 3.用户有购买资格，响应给用户结果；子线程异步执行下单业务
        return Result.ok(orderId);
    }

    /**
     * 异步创建订单
     * @param voucherOrder 优惠券订单对象
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // Notice：这是在子线程内，所以不能从ThreadLocal中取userId
        Long userId = voucherOrder.getUserId();
        // 创建锁对象
        // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        if(!isLock){
            // 获取锁失败,返回错误或重试
            log.error("不允许重复下单");
            return;
        }
        // 获取代理对象（事务）
        try {
            proxy.createVoucherOrder(voucherOrder);
        }finally {
            // 释放锁
            lock.unlock();
        }
    }

    /**
     * 保存订单信息到数据库
     * @param voucherOrder
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder){
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        // 确认一下是否重复下单以防万一
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count > 0){
            // 购买过了
            log.error("不能重复下单");
            return;
        }

        // 6.扣减库存 确认一下
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0) // 乐观锁解决超卖问题
                .update();
        if(!success){
            // 扣减失败
            log.error("库存不足");
            return;
        }

        // 创建订单
        save(voucherOrder);
    }

    /**
     * 秒杀下单原逻辑--->同步执行
     * @param voucherId
     * @return
     */
//    public Result seckillVourcher(Long voucherId) {
//        // 1.查询优惠卷
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        // 2.判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        // 3.判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已经结束");
//        }
//        // 4.判断库存是否充足
//        if(voucher.getStock() < 1){
//            return Result.fail("库存不足");
//        }
//
//        // 5.一人一单---Solved 重复下单问题
//        Long userId = UserHolder.getUser().getId();
//        // 若采用方法内锁代码块的方式，可能出现锁释放但spring管理的事务未提交的情况
//        // 创建锁对象
//        // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 获取锁
//        boolean isLock = lock.tryLock();
//        if(!isLock){
//            // 获取锁失败
//            return Result.fail("不允许重复下单！");
//        }
//        // 获取代理对象（事务）
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        }finally {
//            // 释放锁
//            lock.unlock();
//        }
//    }

    // 初始化线程池

    /**
     * 秒杀逻辑优化——传统阻塞队列
     * @param voucherId 优惠券id
     * @return 返回redis内的抢购结果
     */
//    public Result seckillVourcher(Long voucherId) {
//        // 1.执行秒杀资格判断seckill.lua
//        Long userId = UserHolder.getUser().getId();
//        Long result = stringRedisTemplate.execute(
//                SECKILL_SCRIPT,
//                Collections.emptyList(),
//                voucherId.toString(), userId.toString()
//        );
//        // 2.判断执行结果是否为0
//        int r = result.intValue();
//        if(r != 0){
//            // 2.1不为0没有购买资格
//            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//        }
//        // 2.2有购买资格，把下单信息保存到阻塞队列
//        long orderId = redisIdWorker.nextId("order");
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 2.3订单id
//        voucherOrder.setId(orderId);
//        // 2.4用户id
//        voucherOrder.setUserId(userId);
//        // 2.5代金卷id
//        voucherOrder.setVoucherId(voucherId);
//        // 2.6放入阻塞队列
//        ordersTasks.add(voucherOrder);
//
//        // 3 获取代理对象
//        // 初始化代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//        // 3.返回订单id
//        return Result.ok(orderId);
//    }

    /**
     * 定义线程方法，从阻塞队列中获取下单信息
     */
//    private class VoucherOrderHandle implements Runnable{
//        @Override
//        public void run() {
//            while(true){
//                try {
//                    // 1.获取阻塞队列中的订单信息
//                    VoucherOrder voucherOrder = ordersTasks.take();
//                    // 2.创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (InterruptedException e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }
//    }

}
