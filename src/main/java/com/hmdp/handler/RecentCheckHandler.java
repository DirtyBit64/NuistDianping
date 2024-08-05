package com.hmdp.handler;

import com.hmdp.entity.Shop;
import com.hmdp.entity.UserInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @Author: zhiwei Liao
 * @Date: 2022/9/25 19:26
 * @Description: 用户近期购买的商品是否符合投放的人群
 */
@Slf4j
public class RecentCheckHandler extends AbstractSuggestRequirementHandler{
    @Override
    public void processHandler(UserInfo userInfo, List<Shop> suggestLists) {
        log.debug("RecentCheckHandler");
    }
}
