package com.atguigu.gmall.cart.service;


import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.core.bean.UserInfo;
import com.atguigu.gmall.cart.feign.GmallPmsClient;
import com.atguigu.gmall.cart.feign.GmallSmsClient;
import com.atguigu.gmall.cart.feign.GmallWmsClient;
import com.atguigu.gmall.cart.interceptors.LoginInterceptor;
import com.atguigu.gmall.cart.pojo.Cart;
import com.atguigu.gmall.pms.entity.SkuInfoEntity;
import com.atguigu.gmall.pms.entity.SkuSaleAttrValueEntity;
import com.atguigu.gmall.sms.vo.SaleVO;
import com.atguigu.gmall.wms.entity.WareSkuEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CartService {

    private static final String KEY_PREFIX = "gmall:cart:";
    private static final String PRICE_PREFIC = "gmall:sku:";

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private GmallPmsClient pmsClient;
    @Autowired
    private GmallWmsClient wmsClient;
    @Autowired
    private GmallSmsClient smsClient;

    public void addCart(Cart cart) {

        String key = getLoginState();

        //获取购物车,获取的是用户的hash操作对象
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        String skuId = cart.getSkuId().toString();

        Integer count = cart.getCount();
        //判断购物车中是否有该记录
        if(hashOps.hasKey(skuId)){
            //有，则更新数量
            //获取购物车中的sku记录
            String cartJson = hashOps.get(skuId).toString();
            //反序列化，更新数量
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(cart.getCount()+count);
        }else {
            // 没有，新增购物车记录
            cart.setCheck(true);
            //查询sku相关信息
            Resp<SkuInfoEntity> skuInfoEntityResp = this.pmsClient.querySkuById(cart.getSkuId());
            SkuInfoEntity skuInfoEntity = skuInfoEntityResp.getData();
            if(skuInfoEntity == null){
                return;
            }
            cart.setDefaultImage(skuInfoEntity.getSkuDefaultImg());
            cart.setPrice(skuInfoEntity.getPrice());
            cart.setTitle(skuInfoEntity.getSkuTitle());

            //查询营销（销售）属性
            Resp<List<SkuSaleAttrValueEntity>> listResp = this.pmsClient.querySkuSaleValueBySkuId(cart.getSkuId());
            List<SkuSaleAttrValueEntity> saleAttrValueEntities = listResp.getData();
            cart.setSaleAttrValues(saleAttrValueEntities);

            //查询营销信息
            Resp<List<SaleVO>> saleResp = this.smsClient.querySalesBySkuId(cart.getSkuId());
            List<SaleVO> saleVOS = saleResp.getData();
            cart.setSales(saleVOS);

            //查询库存信息
            Resp<List<WareSkuEntity>> wareResp = this.wmsClient.queryWareSkusBySkuId(cart.getSkuId());
            List<WareSkuEntity> wareSkuEntities = wareResp.getData();
            if(!CollectionUtils.isEmpty(wareSkuEntities)){
                cart.setStore(wareSkuEntities.stream().anyMatch(wareSkuEntity -> wareSkuEntity.getStock()>0));
            }
            this.redisTemplate.opsForValue().set(PRICE_PREFIC+skuId,skuInfoEntity.getPrice().toString());
        }
        //添加到hash缓存
        hashOps.put(skuId,JSON.toJSONString(cart) );


    }

    private String getLoginState() {
        String key = KEY_PREFIX;
        //获取登录状态
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        if (userInfo.getId() != null){
            key += userInfo.getId();
        }else {
            key += userInfo.getUserKey();
        }
        return key;
    }


    public List<Cart> queryCarts() {

        //获取登录状态
        UserInfo userInfo = LoginInterceptor.getUserInfo();

        //查询未登录状态的购物车
        String unloginKey = KEY_PREFIX+userInfo.getUserKey();
        BoundHashOperations<String, Object, Object> unloginHashOps = this.redisTemplate.boundHashOps(unloginKey);
        List<Object> cartJsonList = unloginHashOps.values();

        List<Cart> unloginCarts = null;
        if(!CollectionUtils.isEmpty(cartJsonList)){
            unloginCarts = cartJsonList.stream().map(cartJson -> {
                Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
                //查询当前价格
                String priceString = this.redisTemplate.opsForValue().get(PRICE_PREFIC + cart.getSkuId());
                cart.setCurrentPrice(new BigDecimal(priceString));
                return cart;
            }).collect(Collectors.toList());
        }

        //判断是否登录，未登录，直接返回
        if(userInfo.getId() == null){
            return unloginCarts;
        }

        //登录，购物车同步
        String loginKey = KEY_PREFIX+userInfo.getId();
        BoundHashOperations<String, Object, Object> loginHashOps = redisTemplate.boundHashOps(loginKey);
        if(!CollectionUtils.isEmpty(unloginCarts)){
            unloginCarts.forEach(cart -> {
                Integer count = cart.getCount();
                if(loginHashOps.hasKey(cart.getSkuId().toString())){
                    String cartJson = loginHashOps.get(cart.getSkuId().toString()).toString();
                    cart = JSON.parseObject(cartJson, Cart.class);
                    cart.setCount(cart.getCount()+count);
                }
                loginHashOps.put(cart.getSkuId().toString(),JSON.toJSONString(cart) );
            });
            //同步完成后，删除未登录的购物车
            this.redisTemplate.delete(unloginKey);
        }
        //查询登录状态的购物车
        List<Object> loginCartJsonList = loginHashOps.values();
        return loginCartJsonList.stream().map(cartJson -> {
            Cart cart = JSON.parseObject(cartJson.toString(), Cart.class);
            //查询当前价格
            String priceString = this.redisTemplate.opsForValue().get(PRICE_PREFIC + cart.getSkuId());
            cart.setCurrentPrice(new BigDecimal(priceString));
            return cart;
        }).collect(Collectors.toList());
    }

    public void updateCart(Cart cart) {
        String key = this.getLoginState();
        //获取购物车
        BoundHashOperations<String, Object, Object> boundHashOps = this.redisTemplate.boundHashOps(key);

        Integer count = cart.getCount();
        //判断更新的这条记录，在购物车中有没有
        if(boundHashOps.hasKey(cart.getSkuId().toString())){
            String cartJson = boundHashOps.get(cart.getSkuId()).toString();
            cart = JSON.parseObject(cartJson, Cart.class);
            cart.setCount(count);
            boundHashOps.put(cart.getSkuId(),JSON.toJSONString(cart));
        }
    }

    public void deleteCart(Long skuId) {
        String key = this.getLoginState();
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(key);
        if(hashOps.hasKey(skuId.toString())){
            hashOps.delete(skuId.toString());
        }
    }

    public List<Cart> queryCheckCartsByUserId(Long userId) {
        BoundHashOperations<String, Object, Object> hashOps = this.redisTemplate.boundHashOps(KEY_PREFIX + userId);
        List<Object> cartJsonList = hashOps.values();

        return cartJsonList.stream().map(cartJson ->JSON.parseObject(cartJson.toString(),Cart.class))
                .filter(Cart::getCheck).
                collect(Collectors.toList());
    }
}
