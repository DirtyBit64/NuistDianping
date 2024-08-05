package com.hmdp.handler;

import com.hmdp.entity.Shop;
import com.hmdp.entity.UserInfo;

import java.util.List;

/**
 * @Author: DirtyBit
 * @Date: 2022/9/25 19:26
 * @Description: 良品铺子规则链接口
 */
public abstract class AbstractSuggestRequirementHandler {
    abstract void processHandler(UserInfo userInfo, List<Shop> suggestLists);
}
