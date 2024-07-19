package com.hmdp.mapper;

import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface VoucherOrderMapper extends BaseMapper<VoucherOrder> {

    /**
     * 根据订单状态和理应下单时间查询超时订单
     * @return
     */
    List<VoucherOrder> getByStatusAndOrderTimeLT(Integer status, LocalDateTime orderTime);

    void updateBatch(List<VoucherOrder> voucherOrderList);

}
