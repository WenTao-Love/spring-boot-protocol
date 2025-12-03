package com.github.netty.springboot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Import;

import com.github.netty.springboot.client.NettyRpcClientBeanDefinitionRegistrar;
import com.github.netty.springboot.client.NettyRpcLoadBalanced;
import com.github.netty.springboot.client.NettyRpcRequest;


/**
 * Enable embedded Rpc client protocol
 * It will enable.
 * 1. rpc client protocol. {@link NettyRpcClientBeanDefinitionRegistrar}
 * 
 * You must implement the interface. Returns ip address of the server. {@link NettyRpcLoadBalanced#chooseAddress(NettyRpcRequest)}
 *
 * @author wangzihao 2019-11-2 00:58:38
 * @see NettyProperties
 * @see NettyRpcLoadBalanced#chooseAddress(NettyRpcRequest)
 * @see NettyRpcClientBeanDefinitionRegistrar
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Condition(onBean = NettyProperties.class)
@Import({NettyRpcClientBeanDefinitionRegistrar.class})
public @interface EnableNettyRpcClients {
    String[] value() default {};

    String[] basePackages() default {};
}
