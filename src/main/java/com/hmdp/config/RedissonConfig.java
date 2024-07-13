package com.hmdp.config;

import com.hmdp.constant.RedisConstants;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host}")
    private String ip;
    @Value("${spring.redis.port}")
    private String port;
    @Value("${spring.redis.password}")
    private String pwd;

    @Bean
    public RedissonClient redissonClient(){
        // 配置
        Config config = new Config();
        config.useSingleServer()
                .setAddress(RedisConstants.URL_HEAD + ip + ":" + port)
                .setPassword(pwd);
        // 创建
        return Redisson.create(config);
    }

}
