package com.hmdp.controller;


import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.hmdp.constant.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.OrderCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author ljx
 * @since 2024-7-13
 */
@Slf4j
@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

    @Resource
    private OrderCheckService orderCheckService;

    @PostMapping("seckill/{id}")
    @SentinelResource(value = "seckill", blockHandler = "seckillNoBlockHandler")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return orderCheckService.seckillVourcher(voucherId);
    }

    /**
     * 限流后续操作方法
     * @param id
     * @return
     */
    public static Result seckillNoBlockHandler(Long id, BlockException e) {
        log.info("优惠券{}抢购人数过多，触发热点参数限流!", id);
        return Result.fail(SystemConstants.FLOW_LIMIT_FAIL);
    }
}
