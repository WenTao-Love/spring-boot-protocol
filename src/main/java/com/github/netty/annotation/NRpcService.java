package com.github.netty.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.noear.solon.annotation.Controller;

import com.github.netty.core.util.ApplicationX;

/**
 * RPC service note :(to use RPC, the interface or class can be configured with or without annotations, the default is the class name of the interface)
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ApplicationX.Component
@Controller
//@ResponseBody
public @interface NRpcService {
    /**
     * Default timeout
     */
    int DEFAULT_TIME_OUT = 1000;

    /**
     * Address of the interface
     *
     * @return value
     */
//    @AliasFor(annotation = Controller.class)
    String value() default "";

    /**
     * service version
     *
     * @return any str
     */
    String version() default "";

    /**
     * timeout is -1 then never timeout
     * timeout is 0 then use client timeout
     * timeout other then use server timeout
     *
     * @return method timeout (milliseconds)
     */
    int timeout() default DEFAULT_TIME_OUT;
}
