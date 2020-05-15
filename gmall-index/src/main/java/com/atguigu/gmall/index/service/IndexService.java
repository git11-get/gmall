package com.atguigu.gmall.index.service;


import com.alibaba.fastjson.JSON;
import com.atguigu.core.bean.Resp;
import com.atguigu.gmall.index.annotation.GmallCache;
import com.atguigu.gmall.index.feign.GmallPmaClient;
import com.atguigu.gmall.pms.entity.CategoryEntity;
import com.atguigu.gmall.pms.vo.CategoryVO;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RCountDownLatch;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class IndexService {
    private static final String KEY_PREFIX = "index:cates:";

    @Autowired
    private GmallPmaClient gmallPmaClient;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    public List<CategoryEntity> queryLvl1Categories() {
        Resp<List<CategoryEntity>> listResp = this.gmallPmaClient.queryCategoriesByPidOrLevel(1, null);
        return listResp.getData();
    }

    @GmallCache(prefix = "index:cates:",timeout = 7200,random = 100)
    public List<CategoryVO> querySubCategories(Long pid) {

        // 查询数据库
        Resp<List<CategoryVO>> listResp = this.gmallPmaClient.querySubCategories(pid);
        List<CategoryVO> categoryVOS = listResp.getData();

        return listResp.getData();
    }

    /*  这是使用redisson的分布式锁
    public List<CategoryVO> querySubCategories(Long pid) {

        //1. 判断缓存中有没有
        String cateJson = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);

        //2. 有，直接返回
        if(!StringUtils.isEmpty(cateJson)){
            return JSON.parseArray(cateJson,CategoryVO.class );
        }

        RLock lock = this.redissonClient.getLock("lock" + pid);
        lock.lock();

        //1. 判断缓存中有没有
        String cateJson2 = this.redisTemplate.opsForValue().get(KEY_PREFIX + pid);

        //2. 有，直接返回
        if(!StringUtils.isEmpty(cateJson2)){
            lock.unlock();
            return JSON.parseArray(cateJson2,CategoryVO.class );
        }

        // 查询数据库
        Resp<List<CategoryVO>> listResp = this.gmallPmaClient.querySubCategories(pid);
        List<CategoryVO> categoryVOS = listResp.getData();

        //3. 查询完成又放入缓存
        this.redisTemplate.opsForValue().set(KEY_PREFIX + pid, JSON.toJSONString(categoryVOS),7+new Random().nextInt(5),TimeUnit.DAYS);

        lock.unlock();

        return listResp.getData();
    }
    */


    public void testLock1() {

        //给自己的锁生成一个唯一标志
        String uuid = UUID.randomUUID().toString();

        //执行redis的setnx命令
        Boolean lock = this.redisTemplate.opsForValue().setIfAbsent("lock", uuid,5,TimeUnit.SECONDS);
        //判断是否拿到锁
        if(lock){
            String numString = this.redisTemplate.opsForValue().get("num");
            if(StringUtils.isEmpty(numString)){
                return;
            }
            int num = Integer.parseInt(numString);
            this.redisTemplate.opsForValue().set("num",String.valueOf(++num) );


            // 释放锁资源，其他请求才能执行,判断防止别人误删除
            /*if(StringUtils.equals(this.redisTemplate.opsForValue().get("lock"),uuid )){
                this.redisTemplate.delete("lock");
            }*/
            //这样就具备原子性
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            this.redisTemplate.execute(new DefaultRedisScript<>(script),Arrays.asList("lock"),uuid );
        } else {

            //其他请求重试获取锁
            testLock1();
        }
    }


    public void testLock(){

        RLock lock = this.redissonClient.getLock("lock");
        lock.lock();

        String numString = this.redisTemplate.opsForValue().get("num");
        if(StringUtils.isEmpty(numString)){
            return;
        }
        int num = Integer.parseInt(numString);
        this.redisTemplate.opsForValue().set("num",String.valueOf(++num) );

        lock.unlock();
    }

    public String testRead() {
        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");
        rwLock.readLock().lock();

        String test = this.redisTemplate.opsForValue().get("test");

        rwLock.readLock().unlock();
        return test;
    }

    public String testWrite() {

        RReadWriteLock rwLock = this.redissonClient.getReadWriteLock("rwLock");

        rwLock.writeLock().lock();

        this.redisTemplate.opsForValue().set("test",UUID.randomUUID().toString());

        rwLock.writeLock().unlock();

        return "写入了数据";
    }


    public String testLatch() throws InterruptedException {

        RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");
        latch.trySetCount(5);

        latch.await();
        return "主业务开始执行...";
    }

    public String testCountdown() {

        RCountDownLatch latch = this.redissonClient.getCountDownLatch("latch");

        latch.countDown();

        return "分支业务执行了一次";
    }
}
