package com.hmdp.service;

import com.hmdp.dto.Result;

// 订单资格判定接口，成功对于用户来说下单成功
public interface OrderCheckService {
    Result seckillVourcher(Long voucherId);
}
