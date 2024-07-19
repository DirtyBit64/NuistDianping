package com.hmdp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.constant.CaffeineConstants;
import com.hmdp.entity.Shop;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaffeineConfig {
    @Bean
    public Cache<String, Shop> shopCache(){
        return Caffeine.newBuilder()
                .initialCapacity(CaffeineConstants.SHOP_INIT_SIZE)
                .maximumSize(CaffeineConstants.SHOP_MAX_SIZE)
                //.expireAfterAccess(CaffeineConstants.SHOP_TTL_SECOND, TimeUnit.SECONDS)
                .build();
    }

}
