package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.constant.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.constant.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    @Resource
    private final StringRedisTemplate stringRedisTemplate;
    // 线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private final Random random = new Random(1024);


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 缓存写入方法
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 逻辑过期-缓存写入方法
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit){
        // 设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /** 缓存空值方案
     * 缓存穿透按id查询
     * @param keyPrefix 键名前缀
     * @param id 查询id
     * @param type 类型
     * @param dbFallback 数据库查询函数
     * @param time 缓存TTL时间
     * @param unit 缓存TTL时间单位
     * @return
     * @param <R> 查询结果类型
     * @param <ID> 查询id类型
     */
    public <R, ID> R  queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                           Long time, TimeUnit unit) {
        // 1.从redis查询商铺缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if(StrUtil.isNotBlank(json)){
            // 3.存在有效信息,刷新TTL,并返回
            stringRedisTemplate.expire(key, CACHE_SHOP_TTL + random.nextInt(RANDOM_BOUND), TimeUnit.SECONDS);
            return JSONUtil.toBean(json, type);
        }
        // 判断缓存命中是否为空字符串
        if(json != null){
            // 返回一个错误信息
            return null;
        }
        // 4.不存在，查询数据库
        R r = dbFallback.apply(id);
        // 5.数据库不存在，返回错误
        if(r == null){
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
            // 返回错误信息
            return null;
        }
        // 6.存在，写入Redis 随机ttl防止雪崩
        this.set(key, r, time + random.nextInt(RANDOM_BOUND), unit);
        // 7.返回
        return r;
    }

    /** 逻辑过期方案
     * 缓存击穿
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param lockKeyPrefix
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback,
                                            String lockKeyPrefix, Long time, TimeUnit unit) {
        // 1.从redis查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if(StrUtil.isBlank(json)){
            // 3.不存在有效店铺信息，直接返回空值
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);
        // 5.判断是否逻辑过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            // 5.1未过期，直接返回查询结果
            return r;
        }
        // 5.2已过期，需要缓存重建
        // 6.缓存重建
        // 6.1获取互斥锁
        String lockKey = lockKeyPrefix + id;
        boolean isLock = tryLock(lockKey);
        // 6.2判断是否获取锁成功
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R r1 = dbFallback.apply(id);
                    // 更新缓存
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unLock(lockKey);
                }
            });
        }
        // 6.4返回过期数据
        return r;
    }

    /**
     * 获取互斥锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "芜湖", LOCK_SHOP_TTL, TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
