package com.atguigu.gmall.order.service;


import com.atguigu.core.bean.Resp;
import com.atguigu.core.bean.UserInfo;
import com.atguigu.core.exception.OrderException;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.oms.entity.OrderEntity;
import com.atguigu.gmall.oms.vo.OrderItemVO;
import com.atguigu.gmall.oms.vo.OrderSubmitVO;
import com.atguigu.gmall.order.feign.*;
import com.atguigu.gmall.order.interceptors.LoginInterceptor;
import com.atguigu.gmall.order.vo.OrderConfirmVO;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.ums.entity.MemberEntity;
import com.atguigu.gmall.ums.entity.MemberReceiveAddressEntity;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import com.atguigu.gmall.wms.vo.SkuLockVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class OrderService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallOmsClient omsClient;
    @Autowired
    private GmallUmsClient umsClient;
    @Autowired
    private GmallSmsClient smsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GmallCartClient cartClient;
    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;
    @Autowired
    private AmqpTemplate amqpTemplate;

    private static final String TOKEN_PREFIX = "order:token:";

    public OrderConfirmVO confirm() {

        OrderConfirmVO orderConfirmVO = new OrderConfirmVO();

        UserInfo userInfo = LoginInterceptor.getUserInfo();
        Long userId = userInfo.getId();
        if (userId == null) {
            return null;
        }

//        List<CompletableFuture> futures = new ArrayList<>();

        // 获取用户的收货地址列表,根据用户id查询收货地址列表接口
        CompletableFuture<Void> addressCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<List<MemberReceiveAddressEntity>> addressResp = this.umsClient.queryAddressByUserId(userId);
            List<MemberReceiveAddressEntity> memberReceiveAddressEntities = addressResp.getData();
            orderConfirmVO.setAddress(memberReceiveAddressEntities);
        }, threadPoolExecutor);


        //获取购物车中选中的商品信息: skuId count
        CompletableFuture<Void> bigSkuCompletableFuture = CompletableFuture.supplyAsync(() -> {
            Resp<List<Cart>> cartsResp = this.cartClient.queryCheckCartsByUserId(userId);
            List<Cart> cartList = cartsResp.getData();
            if (CollectionUtils.isEmpty(cartList)) {
                throw new OrderException("请勾选购物车商品");
            }
            return cartList;

        }, threadPoolExecutor).thenAcceptAsync(cartList -> {
            List<OrderItemVO> itemVOS = cartList.stream().map(cart -> {
                OrderItemVO orderItemVO = new OrderItemVO();
                Long skuId = cart.getSkuId();

                CompletableFuture<Void> skuCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(skuId);
                    SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();

                    if (skuInfoEntity != null) {
                        orderItemVO.setSkuId(skuId);
                        orderItemVO.setCount(cart.getCount());

                        orderItemVO.setWeight(skuInfoEntity.getWeight());
                        orderItemVO.setDefaultImage(skuInfoEntity.getSkuDefaultImg());
                        orderItemVO.setPrice(skuInfoEntity.getPrice());
                        orderItemVO.setTitle(skuInfoEntity.getSkuTitle());
                    }
                }, threadPoolExecutor);

                //销售属性信息
                CompletableFuture<Void> saleAttrCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<List<SkuSaleAttrValueEntity>> saleAttrValueResp = this.pmsClient.querySkuSaleValueBySkuId(skuId);
                    List<SkuSaleAttrValueEntity> attrValueEntities = saleAttrValueResp.getData();
                    orderItemVO.setSaleAttrValues(attrValueEntities);
                }, threadPoolExecutor);

                //库存信息
                CompletableFuture<Void> wareSkuCompletableFuture = CompletableFuture.runAsync(() -> {
                    Resp<List<WareSkuEntity>> wareResp = this.wmsClient.queryWareSkusBySkuId(skuId);
                    List<WareSkuEntity> wareSkuEntities = wareResp.getData();
                    if (!CollectionUtils.isEmpty(wareSkuEntities)) {
                        orderItemVO.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock() > 0));
                    }
                }, threadPoolExecutor);

                CompletableFuture.allOf(skuCompletableFuture, saleAttrCompletableFuture, wareSkuCompletableFuture).join();

                return orderItemVO;
            }).collect(Collectors.toList());
            orderConfirmVO.setOrderItems(itemVOS);

        }, threadPoolExecutor);


        //查询用户信息，获取积分（赠送积分) 根据id查询用户信息，用户信息里就有
        CompletableFuture<Void> memberCompletableFuture = CompletableFuture.runAsync(() -> {
            Resp<MemberEntity> memberEntityResp = this.umsClient.queryMemberById(userId);
            MemberEntity memberEntity = memberEntityResp.getData();
            orderConfirmVO.setBounds(memberEntity.getIntegration());

        }, threadPoolExecutor);

        // 生成一个唯一标识，防止重复提交(响应到页面一份，另一份保存到redis)
        CompletableFuture<Void> tokenCompletableFuture = CompletableFuture.runAsync(() -> {
            String orderToken = IdWorker.getIdStr();
            orderConfirmVO.setOrderToken(orderToken);
            this.redisTemplate.opsForValue().set(TOKEN_PREFIX+orderToken,orderToken);
        }, threadPoolExecutor);

        CompletableFuture.allOf(
                addressCompletableFuture,
                bigSkuCompletableFuture,
                memberCompletableFuture,
                tokenCompletableFuture
                );

        return orderConfirmVO;
    }

    public OrderEntity submit(OrderSubmitVO orderSubmitVO) {

        UserInfo userInfo = LoginInterceptor.getUserInfo();


        //获取orderToken
        String orderToken = orderSubmitVO.getOrderToken();

        //1. 防重复提交，查询redis中有没有orderToken信息，有，则是第一次提交，放行并删除redis中的orderToken
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        //参数一script：redis脚本; 参数二keys：key的集合； 参数三args：不定参数
        Long flag = this.redisTemplate.execute(new DefaultRedisScript<>(script,Long.class), Arrays.asList(TOKEN_PREFIX + orderToken), orderToken);
        if(flag == 0){
            throw new OrderException("订单不可重复提交");
        }

        //2. 校验价格，总价一致放行
        List<OrderItemVO> items = orderSubmitVO.getItems(); //送货清单
        BigDecimal totalPrice = orderSubmitVO.getTotalPrice();//总价

        if(CollectionUtils.isEmpty(items)){
            throw new OrderException("没有购买的商品，请到购物车中勾选商品");
        }
        //获取实时总价信息
        BigDecimal currentTotalPrice = items.stream().map(item -> {
            Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(item.getSkuId());
            SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
            if (skuInfoEntity != null) {
                return skuInfoEntity.getPrice().multiply(new BigDecimal(item.getCount()));
            }
            return new BigDecimal(0);
        }).reduce((a, b) -> a.add(b)).get();

        //判断实时总价和页面的总价格是否一致
        if(currentTotalPrice.compareTo(totalPrice) !=0){
            throw new OrderException("页面已过期，请刷新页面后重新下单！");
        }

        //3. 校验库存是否充足，并锁定库存，一次性提示所有库存不够的商品信息（远程接口待开发）
        List<SkuLockVO> lockVOS = items.stream().map(orderItemVO -> {
            SkuLockVO skuLockVO = new SkuLockVO();
            skuLockVO.setSkuId(orderItemVO.getSkuId());
            skuLockVO.setCount(orderItemVO.getCount());
            skuLockVO.setOrderToken(orderToken);
            return skuLockVO;
        }).collect(Collectors.toList());
        Resp<Object> wareResp = this.wmsClient.checkAndLockStore(lockVOS);
        if(wareResp.getCode() != 0){
            throw new OrderException(wareResp.getMsg());
        }

        //4. 下单（创建订单及订单详细，远程接口待开发）
        Resp<OrderEntity> orderEntityResp = null;
        try {
            orderSubmitVO.setUserId(userInfo.getId());
            orderEntityResp = this.omsClient.saveOrder(orderSubmitVO);
        } catch (Exception e) {
            e.printStackTrace();
            //发送消息给wms，解锁对应的库存
            this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE","stock.unlock",orderToken);
            throw  new OrderException("服务器错误，创建订单失败！");
        }


        //5. 删除购物车（发送信息删除购物车）
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userInfo.getId());
        List<Long> skuIds = items.stream().map(OrderItemVO::getSkuId).collect(Collectors.toList());
        map.put("skuIds", skuIds);
        this.amqpTemplate.convertAndSend("GMALL-ORDER-EXCHANGE","cart.delete",map);

        if(orderEntityResp != null){
            return orderEntityResp.getData();
        }
        return null;

    }
}






















