package com.hmdp.handler;

import com.hmdp.entity.Shop;
import com.hmdp.entity.UserInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @Author: zhiwei Liao
 * @Date: 2022/9/25 19:26
 * @Description: 用户资质是否满足业务投放资质
 */
@Slf4j
public class PersonalCheckHandler extends AbstractSuggestRequirementHandler{
    @Override
    public void processHandler(UserInfo userInfo, List<Shop> suggestLists) {
        log.debug("PersonalCheckHandler");
    }
}