package com.atguigu.gmall.auth;

import com.atguigu.core.utils.JwtUtils;
import com.atguigu.core.utils.RsaUtils;
import org.junit.Before;
import org.junit.Test;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

public class JwtTest {
	private static final String pubKeyPath = "D:\\temp\\rsa\\rsa.pub";

    private static final String priKeyPath = "D:\\temp\\rsa\\rsa.pri";

    private PublicKey publicKey;

    private PrivateKey privateKey;

    @Test
    public void testRsa() throws Exception {
        RsaUtils.generateKey(pubKeyPath, priKeyPath, "234");
    }

    @Before
    public void testGetRsa() throws Exception {
        this.publicKey = RsaUtils.getPublicKey(pubKeyPath);
        this.privateKey = RsaUtils.getPrivateKey(priKeyPath);
    }

    @Test
    public void testGenerateToken() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "11");
        map.put("username", "liuyan");
        // 生成token
        String token = JwtUtils.generateToken(map, privateKey, 1);
        System.out.println("token = " + token);
    }

    @Test
    public void testParseToken() throws Exception {
        String token = "eyJhbGciOiJSUzI1NiJ9.eyJpZCI6IjExIiwidXNlcm5hbWUiOiJsaXV5YW4iLCJleHAiOjE1ODg5Mjc5NzR9.OxxdRTRgSMU7D6dl68cWEm2DZa9HylTJ0fYTG4j2sLY1Cswj1geHSdCdU3imPGwNXBkEfOXRf4k2E3AZjXdGmCSuXt0YvnUecJJcSM4nLjOsoXuQ7kJpFkU9UIM8prik1Wc9LKLEu1a4bOoUVM6dPyLhn8CvDi_PbZcgnP2yiy2FNPhDEsL-ha-gziVHkuGjYlBxJeV6TVft5eU8KyocnzLXljzOQlSW8vYqVAaPOhba-NBKpbDMTijE2sbznJk0ygk9eKZTW5ftJf9pO7UkbFvtxlvYpL-XpHbAl2Rb6KIL4rr0BcDD0uCkatjtxcDFac9-3Bw06Xtb4XFhIRd6PA";

        // 解析token
        Map<String, Object> map = JwtUtils.getInfoFromToken(token, publicKey);
        System.out.println("id: " + map.get("id"));
        System.out.println("userName: " + map.get("username"));
    }
}