package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hmdp.constant.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author DirtyBit
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 商户类型缓存
     * @return
     */
    public Result typeList() {
        // 1.查询Redis中是否存在
        Set<String> jsonShopTypes = stringRedisTemplate.opsForZSet().range(CACHE_SHOPTYPE_KEY, 0, -1);
        // 2.存在，封装到list容器并返回
        if(jsonShopTypes != null && !jsonShopTypes.isEmpty()){
            List<ShopType> shopTypeList = new ArrayList<>();
            for (String jsonShopType : jsonShopTypes) {
                ShopType shopType = JSONUtil.toBean(jsonShopType, ShopType.class);
                shopTypeList.add(shopType);
            }
            return Result.ok(shopTypeList);
        }
        // 3.不存在，查询数据库
        List<ShopType> shopTypeList = query().list();
        // 4.数据库里没有，返回空
        if(shopTypeList == null || shopTypeList.isEmpty()){
            return Result.fail("没有商铺分类信息");
        }
        // 5.数据库里有，加入缓存
        for (ShopType shopType : shopTypeList) {
            String jsonShopType = JSONUtil.toJsonStr(shopType);
            stringRedisTemplate.opsForZSet().add(CACHE_SHOPTYPE_KEY, jsonShopType, shopType.getSort());
        }
        // 6.返回结果
        return Result.ok(shopTypeList);
    }
}
