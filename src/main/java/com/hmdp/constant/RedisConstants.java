package com.hmdp.constant;

public class RedisConstants {
    public static final String URL_HEAD = "redis://";
    // id生成器
    public static final String ORDER_ID_KEY_PREFIX = "order";
    // 登录验证码相关
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;

    // 缓存相关
    public static final Long CACHE_NULL_TTL = 5L;
    public static final Long CACHE_SHOP_TTL = 300L;
    public static final int RANDOM_BOUND = 100;
    public static final String CACHE_SHOP_KEY = "cache:shop:";
    public static final String CACHE_SHOPTYPE_KEY = "cache:shoptype";
    // 分布式锁
    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final String LOCK_ORDER_KEY = "lock:order:";
    public static final Long LOCK_SHOP_TTL = 10L;

    // 业务相关
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
    // 秒杀相关
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_ORDER_KEY = "seckill:order:";
}
