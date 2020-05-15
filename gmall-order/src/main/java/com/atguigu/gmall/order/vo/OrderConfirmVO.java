package com.atguigu.gmall.order.vo;


import com.atguigu.gmall.oms.vo.OrderItemVO;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import lombok.Data;

import java.util.List;

@Data
public class OrderConfirmVO {

    private List<MemberReceiveAddressEntity> address; //收货地址
    private List<OrderItemVO> orderItems; //订单详情。与购物车相似，但不能直接用
    private Integer bounds;  //积分
    private String orderToken;  //订单的唯一标志，防止重复提交
}


