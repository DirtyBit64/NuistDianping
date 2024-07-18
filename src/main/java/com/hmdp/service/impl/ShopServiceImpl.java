package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constant.ShopConstants;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.constant.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.hmdp.constant.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    /** TODO 咖啡因
     * 根据id查询商户信息
     * @param id
     * @return
     */
    public Result queryById(Long id) {
        // 逻辑过期解决缓存击穿
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, LOCK_SHOP_KEY, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail(ShopConstants.SHOP_NOT_FOUND);
        }
        // 返回
        return Result.ok(shop);
    }

    /**
     * 缓存击穿互斥锁解决方案
     * @param id
     * @return
     */
    @Deprecated
    public Shop queryWithMutex(Long id){
        // 1.从redis查询商铺缓存
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 3.存在有效商铺信息，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 如果命中空字符串
        if(shopJson != null){
            // 返回错误信息
            return null;
        }

        // 4.实现缓存重建
        // 4.1获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2获取失败.休眠并重查缓存 递归
            if(!isLock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            // 4.3成功拿到锁，(可做Double check),根据id查询数据库
            shop = getById(id);
            // 5.数据库不存在，返回错误
            if(shop == null){
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在，写入Redis
            shopJson = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(shopKey, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7.释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }

    /**
     * 缓存穿透解决方案
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        // 1.从redis查询商铺缓存
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2.判断是否存在
        if(StrUtil.isNotBlank(shopJson)){
            // 3.存在有效商铺信息，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断缓存命中是否为空字符串
        if(shopJson != null){
            // 返回一个错误信息
            return null;
        }
        // 4.不存在，查询数据库
        Shop shop = getById(id);
        // 5.数据库不存在，返回错误
        if(shop == null){
            // 将空值写入redis
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回错误信息
            return null;
        }
        // 6.存在，写入Redis
        shopJson = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(shopKey, shopJson, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 7.返回
        return shop;
    }

    /**
     * 更新店铺信息
     * @param shop
     * @return
     */
    @Transactional
     public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail(ShopConstants.SHOP_ID_EMPTY);
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }

    /**
     * 根据type滚动分页查询商铺
     * @param typeId 商铺类型id
     * @param current 当前页码
     * @param x 如果非null，则表示按照坐标查
     * @param y 如果非null，则表示按照坐标查
     * @return 返回分页查询结果
     */
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if(x == null || y == null){
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = from + SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页：shopId,distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key, GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出shopId
        if(results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        // 4.1从0-end截取出from到end数据
        if(content.size() <= from){
            // 没有下一页
            return Result.ok(Collections.emptyList());
        }
        // 要反馈给用户的商铺id结合
        List<Long> ids = new ArrayList<>(content.size());
        Map<String, Distance> distanceMap = new HashMap<>(content.size());
        content.stream().skip(from).forEach(result -> {
            // 获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据ids桉序查询Shop
        // 5.1 采用管道去Redis中批量查询商铺缓存信息
        List<Object> shopJsonList = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
            for (Long id : ids) {
                String shopKey = CACHE_SHOP_KEY + id;
                connection.get(shopKey.getBytes());
            }
            return null;
        });
        // 5.2 处理结果
        List<Shop> shops = new ArrayList<>();
        for (Object object : shopJsonList) {
            if (object == null) {
                // 商铺信息无效，直接跳过
                continue;
            }
            String shopStr = (String) object;
            // 5.3 命中，需要先把json反序列化为对象
            RedisData redisData = JSONUtil.toBean(shopStr, RedisData.class);
            JSONObject data = (JSONObject) redisData.getData();
            Shop shop = JSONUtil.toBean(data, Shop.class);
            // 弱一致性：逻辑过期与否都直接返回，当用户点击具体店铺信息时再缓存重建
            // 5.5 绑定distance，并反馈用户
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
            shops.add(shop);
        }

        // 6.返回
        return Result.ok(shops);
    }

    // 若采用逻辑过期方式缓存店铺信息，则在应用启动前从数据库读取数据到redis
    public void saveShop2Redis(Long id, Long expireSeconds){
        // 1.查询店铺数据
        Shop shop = getById(id);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3.写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 获取互斥锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "哦吼", LOCK_SHOP_TTL, TimeUnit.MINUTES);
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
