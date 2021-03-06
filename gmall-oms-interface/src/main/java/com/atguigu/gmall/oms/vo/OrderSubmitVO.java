package com.atguigu.gmall.oms.vo;

import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderSubmitVO {
    private String orderToken; //防重
    private MemberReceiveAddressEntity address;
    private Integer payType;
    private String deliveryCompany;
    private List<OrderItemVO> items;
    private Integer bounds; //积分
    private BigDecimal totalPrice;//校验价格
    private Long userId; //用户id
}
