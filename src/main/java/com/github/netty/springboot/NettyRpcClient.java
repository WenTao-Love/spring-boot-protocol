package com.github.netty.springboot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.noear.solon.annotation.Component;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Component
public @interface NettyRpcClient {
    /**
     * The serviceName is the same as serviceName
     * example value "service-provider"
     *
     * @return serviceName
     */
    String serviceName();

//    Class<?> fallback() default void.class;

    /**
     * Timeout time (milliseconds)
     *
     * @return timeout
     */
    int timeout() default -1;

//    int retry() default -1;

}
