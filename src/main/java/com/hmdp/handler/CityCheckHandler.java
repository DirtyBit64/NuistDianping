package com.hmdp.handler;

import com.hmdp.entity.Shop;
import com.hmdp.entity.UserInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @Author: zhiwei Liao
 * @Date: 2022/9/25 19:26
 * @Description: 用户所在城市是否满足业务投放城市
 */
@Slf4j
public class CityCheckHandler extends AbstractSuggestRequirementHandler{
    @Override
    void processHandler(UserInfo userInfo, List<Shop> shopList) {
        log.debug("CityCheckHandler");
    }
}
