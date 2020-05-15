package com.atguigu.gmall.oms.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.oms.dao.OrderItemDao;
import com.atguigu.gmall.oms.entity.OrderItemEntity;
import com.atguigu.gmall.oms.feign.GmallPmsClient;
import com.atguigu.gmall.oms.feign.GmallUmsClient;
import com.atguigu.gmall.oms.feign.GmallWmsClient;
import com.atguigu.gmall.oms.vo.OrderItemVO;
import com.atguigu.gmall.oms.vo.OrderSubmitVO;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SpuInfoEntity;
import com.atguigu.gmall.ums.entity.MemberEntity;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.core.bean.PageVo;
import com.atguigu.core.bean.Query;
import com.atguigu.core.bean.QueryCondition;

import com.atguigu.gmall.oms.dao.OrderDao;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.service.OrderService;
import org.springframework.transaction.annotation.Transactional;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    @Autowired
    private GmallUmsClient umsClient;
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private OrderItemDao orderItemDao;
    @Autowired
    private AmqpTemplate amqpTemplate;

    @Override
    public PageVo queryPage(QueryCondition params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageVo(page);
    }

    @Transactional
    @Override
    public OrderEntity saveOrder(OrderSubmitVO submitVO) {

        //保存订单OrderEntity
        MemberReceiveAddressEntity address = submitVO.getAddress();
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setReceiverRegion(address.getRegion());
        orderEntity.setReceiverProvince(address.getProvince());
        orderEntity.setReceiverPostCode(address.getPostCode());
        orderEntity.setReceiverPhone(address.getPhone());
        orderEntity.setReceiverName(address.getName());
        orderEntity.setReceiverDetailAddress(address.getDetailAddress());
        orderEntity.setReceiverCity(address.getCity());

        Resp<MemberEntity> memberEntityResp = this.umsClient.queryMemberById(submitVO.getUserId());
        MemberEntity memberEntity = memberEntityResp.getData();
        orderEntity.setMemberUsername(memberEntity.getUsername());
        orderEntity.setMemberId(submitVO.getUserId());
        //清算每个商品赠送积分
        orderEntity.setIntegration(0);
        orderEntity.setGrowth(0);

        orderEntity.setDeleteStatus(0);
        orderEntity.setStatus(0);

        orderEntity.setCommentTime(new Date());
        orderEntity.setModifyTime(orderEntity.getCommentTime());
        orderEntity.setDeliveryCompany(submitVO.getDeliveryCompany());
        orderEntity.setSourceType(1);
        orderEntity.setPayType(submitVO.getPayType());
        orderEntity.setTotalAmount(submitVO.getTotalPrice());
        orderEntity.setOrderSn(submitVO.getOrderToken());

        this.save(orderEntity);
        Long orderId = orderEntity.getId();



        //保存订单详情OrderItemEntity
        List<OrderItemVO> items = submitVO.getItems();
        items.forEach(item->{

            OrderItemEntity orderItemEntity = new OrderItemEntity();
            orderItemEntity.setSkuId(item.getSkuId());

            Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(item.getSkuId());
            SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();

            Resp<SpuInfoEntity> spuInfoEntityResp = this.pmsClient.querySpuById(skuInfoEntity.getSpuId());
            SpuInfoEntity spuInfoEntity = spuInfoEntityResp.getData();

            orderItemEntity.setSkuPrice(skuInfoEntity.getPrice());
            orderItemEntity.setSkuAttrsVals(JSON.toJSONString(item.getSaleAttrValues()));
            orderItemEntity.setCategoryId(skuInfoEntity.getCatalogId());
            orderItemEntity.setOrderId(orderId);
            orderItemEntity.setOrderSn(submitVO.getOrderToken());
            orderItemEntity.setSpuId(spuInfoEntity.getId());
            orderItemEntity.setSkuName(skuInfoEntity.getSkuName());
            orderItemEntity.setSkuPic(skuInfoEntity.getSkuDefaultImg());
            orderItemEntity.setSkuQuantity(item.getCount());
            orderItemEntity.setSpuName(spuInfoEntity.getSpuName());

            this.orderItemDao.insert(orderItemEntity);
        });

        //订单创建之后，在响应之前发送延时消息，达到定时关单的效果
        this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE","order.ttl",submitVO.getOrderToken());

        return orderEntity;
    }



}