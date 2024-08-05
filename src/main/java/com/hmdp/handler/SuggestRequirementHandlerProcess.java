package com.hmdp.handler;

import com.hmdp.constant.ShopConstants;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IShopService;
import com.hmdp.service.IUserInfoService;
import com.hmdp.utils.UserHolder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 良品铺子板块
 */
@Data
@Component
@ConfigurationProperties(prefix = "lppz")
public class SuggestRequirementHandlerProcess {
    // 过滤规则
    private List<String> handlers;
    // 公司选出来的良品铺子
    private List<String> niceShopNames;

    @Resource
    public IShopService shopService;

    public Result process(Integer current){
        // 1.查出当前用户信息和商户集合
        UserDTO user = UserHolder.getUser();
        if(user == null || niceShopNames == null){
            return Result.fail(SystemConstants.ROUTE_LOGIN);
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setCity("南京");
        List<Shop> shopList = shopService.queryShopByName(niceShopNames);
        if(shopList == null || shopList.isEmpty()){
            return Result.ok(ShopConstants.LPPZ_EMPTY);
        }

        // 2.规则链过滤
        for(String handler : handlers) {
            try {
                AbstractSuggestRequirementHandler handle = (AbstractSuggestRequirementHandler)
                        Class.forName(SystemConstants.HANDLER_PREFIX + handler).newInstance();
                // 2.1对投放商户集合过滤
                handle.processHandler(userInfo, shopList);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }
        // 3.滚动分页反馈给前端
        if (shopList.size() > SystemConstants.DEFAULT_PAGE_SIZE){
            List<Shop> newShopList = new ArrayList<>();
            for(int i=current-1;i<current+SystemConstants.DEFAULT_PAGE_SIZE;++i){
                newShopList.add(shopList.get(i));
            }
            return Result.ok(newShopList);
        }
        return Result.ok(shopList);
    }
}
