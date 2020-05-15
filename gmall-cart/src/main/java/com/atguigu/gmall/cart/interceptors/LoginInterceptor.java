package com.atguigu.gmall.cart.interceptors;

import com.atguigu.core.bean.UserInfo;
import com.atguigu.core.utils.CookieUtils;
import com.atguigu.core.utils.JwtUtils;
import com.atguigu.gmall.cart.config.JwtProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;


@Component
@EnableConfigurationProperties(JwtProperties.class)
public class LoginInterceptor extends HandlerInterceptorAdapter {


    @Autowired
    private JwtProperties jwtProperties;

    private static final ThreadLocal<UserInfo> THREAD_LOCAL = new ThreadLocal<>();


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        UserInfo userInfo = new UserInfo();

        //获取cookie中token信息（jwt）及userKey信息
        //已经登录 携带的标识
        String token = CookieUtils.getCookieValue(request, this.jwtProperties.getCookieName());
        //未登录 游客的唯一标识
        String userKey = CookieUtils.getCookieValue(request, this.jwtProperties.getUserKey());

        //判断有没有userKey。 没有：制作一个放入cookie中
        if(StringUtils.isEmpty(userKey)){
            userKey = UUID.randomUUID().toString();
            CookieUtils.setCookie(request,response,this.jwtProperties.getUserKey(), userKey,6 * 30 * 24 * 3600);
        }
        userInfo.setUserKey(userKey);

        //判断有没有token
        if(StringUtils.isNotBlank(token)){
            //解析token信息
            Map<String, Object> infoFromToken = JwtUtils.getInfoFromToken(token, this.jwtProperties.getPublicKey());
            userInfo.setId(new Long(infoFromToken.get("id").toString()));
        }

        THREAD_LOCAL.set(userInfo );

         return super.preHandle(request, response, handler);
    }

    public static UserInfo getUserInfo(){
        return THREAD_LOCAL.get();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //必须收到清除ThreadLocal中的线程变量，因为使用的tomcat线程池，造成内存泄露
        THREAD_LOCAL.remove();
    }
}
