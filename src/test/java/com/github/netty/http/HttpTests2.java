package com.github.netty.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.Assert;

import com.github.netty.core.util.IOUtil;

import cn.hutool.http.HttpUtil;

//@SpringBootTest(classes = HttpTests2.class)
public class HttpTests2 {
    @Test
    public void test() throws IOException {
    	String responseBody = HttpUtil.get("http://localhost:8080/testHello?name=xiaowang");
    	System.out.println(responseBody);
//    	AntPathMatcher
//        URL url = new URL("http://localhost:8080/hello?name=xiaowang");
//        InputStream inputStream = url.openStream();
//        String responseBody = IOUtil.readInput(inputStream);
        Assert.isTrue(Objects.equals("hi! xiaowang", responseBody),"no hi! xiaowang");
    }
}
