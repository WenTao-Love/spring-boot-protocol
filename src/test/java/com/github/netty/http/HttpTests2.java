package com.github.netty.http;

import cn.hutool.http.HttpUtil;
import org.junit.jupiter.api.Test;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.Objects;

//@SpringBootTest(classes = HttpTests2.class)
public class HttpTests2 {
    @Test
    public void test() throws IOException {
    	String responseBody = HttpUtil.get("http://localhost:8080/hello?name=xiaowang");
    	System.out.println(responseBody);
//        URL url = new URL("http://localhost:8080/hello?name=xiaowang");
//        InputStream inputStream = url.openStream();
//        String responseBody = IOUtil.readInput(inputStream);
        Assert.isTrue(Objects.equals("hi! xiaowang", responseBody),"no hi! xiaowang");
    }
}
