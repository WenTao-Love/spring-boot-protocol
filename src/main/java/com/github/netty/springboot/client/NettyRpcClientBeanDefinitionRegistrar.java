package com.github.netty.springboot.client;

import com.github.netty.core.util.ApplicationX;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.codec.DataCodecUtil;
import com.github.netty.springboot.EnableNettyRpcClients;
import com.github.netty.springboot.NettyProperties;
import com.github.netty.springboot.NettyRpcClient;

import java.beans.Introspector;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Supplier;

/**
 * Scan rpc interfaces and definition bean using Solon style
 *
 * @author wangzihao
 */
public class NettyRpcClientBeanDefinitionRegistrar {
    private final LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private final String enableNettyRpcClientsCanonicalName = EnableNettyRpcClients.class.getCanonicalName();
    private final String nettyRpcClientCanonicalName = NettyRpcClient.class.getCanonicalName();
    private Supplier<NettyRpcLoadBalanced> nettyRpcLoadBalancedSupplier;
    private Supplier<NettyProperties> nettyPropertiesSupplier;
    private ApplicationX applicationX;

    public NettyRpcClientBeanDefinitionRegistrar() {
    }

    /**
     * 注册RPC客户端Bean
     * @param applicationX 应用上下文
     * @param importingClass 导入类
     */
    public void registerRpcClients(ApplicationX applicationX, Class<?> importingClass) {
        this.applicationX = applicationX;
        
        // 初始化Bean获取器
        this.nettyRpcLoadBalancedSupplier = () -> applicationX.getBean(NettyRpcLoadBalanced.class);
        this.nettyPropertiesSupplier = () -> {
            NettyProperties properties = applicationX.getBean(NettyProperties.class);
            logger.info("used codec = {}", DataCodecUtil.getDataCodec());
            this.nettyPropertiesSupplier = () -> properties;
            return properties;
        };

        // 获取注解信息
        EnableNettyRpcClients annotation = importingClass.getAnnotation(EnableNettyRpcClients.class);
        Set<String> basePackages = getBasePackages(importingClass, annotation);

        // 扫描并注册RPC客户端
        for (String basePackage : basePackages) {
            scanAndRegisterRpcClients(basePackage);
        }

        // 注册Bean后处理器
        applicationX.addBeanPostProcessor(new SolonBeanPostProcessor());
    }

    private void scanAndRegisterRpcClients(String basePackage) {
        try {
            // 使用ApplicationX的扫描功能
            ApplicationX.ScannerResult scannerResult = applicationX.scanner(basePackage);
            
            // 处理扫描到的类
            for (Map.Entry<String, ApplicationX.BeanDefinition> entry : scannerResult.getBeanDefinitionMap().entrySet()) {
                ApplicationX.BeanDefinition definition = entry.getValue();
                Class<?> beanClass = definition.getBeanClassIfResolve(applicationX.getClass()::getClassLoader);
                
                // 检查是否有NettyRpcClient注解
                NettyRpcClient nettyRpcClient = beanClass.getAnnotation(NettyRpcClient.class);
                if (nettyRpcClient != null && beanClass.isInterface()) {
                    registerNettyRpcClient(beanClass, nettyRpcClient);
                }
            }
        } catch (Exception e) {
            logger.error("Scan RPC clients failed for package: {}", basePackage, e);
        }
    }

    private void registerNettyRpcClient(Class<?> beanClass, NettyRpcClient annotation) {
        String serviceName = annotation.serviceName();
        int timeout = annotation.timeout();
        // 移除Spring的Lazy注解依赖，使用默认非延迟初始化或自定义逻辑
        boolean isLazy = false; // 默认非延迟初始化

        // 创建Bean定义
        ApplicationX.BeanDefinition definition = applicationX.newBeanDefinition(beanClass);
        definition.setLazyInit(isLazy);
        definition.setBeanSupplier(newInstanceSupplier(beanClass, serviceName, timeout));

        // 生成Bean名称
        String beanName = generateBeanName(beanClass.getName());
        
        // 注册Bean定义
        applicationX.addBeanDefinition(beanName, definition);
    }

    public <T> Supplier<T> newInstanceSupplier(Class<T> beanClass, String serviceName, int timeout) {
        return () -> {
            NettyProperties nettyProperties = nettyPropertiesSupplier.get();
            NettyRpcClientProxy nettyRpcClientProxy = new NettyRpcClientProxy(serviceName, null,
                    beanClass, nettyProperties,
                    nettyRpcLoadBalancedSupplier);
            if (timeout > 0) {
                nettyRpcClientProxy.setTimeout(timeout);
            }
            Object instance = Proxy.newProxyInstance(beanClass.getClassLoader(), 
                    new Class[]{beanClass, RpcClient.Proxy.class}, 
                    nettyRpcClientProxy);
            return (T) instance;
        };
    }

    public String generateBeanName(String beanClassName) {
        int lastDotIndex = beanClassName.lastIndexOf('.');
        String shortName = lastDotIndex > 0 ? beanClassName.substring(lastDotIndex + 1) : beanClassName;
        return Introspector.decapitalize(shortName);
    }

    protected Set<String> getBasePackages(Class<?> importingClass, EnableNettyRpcClients annotation) {
        Set<String> basePackages = new HashSet<>();
        if (annotation != null) {
            for (String pkg : annotation.value()) {
                if (pkg != null && !pkg.trim().isEmpty()) {
                    basePackages.add(pkg.trim());
                }
            }
            for (String pkg : annotation.basePackages()) {
                if (pkg != null && !pkg.trim().isEmpty()) {
                    basePackages.add(pkg.trim());
                }
            }
        }

        if (basePackages.isEmpty()) {
            basePackages.add(importingClass.getPackage().getName());
        }
        return basePackages;
    }

    /**
     * Solon风格的Bean后处理器
     */
    private class SolonBeanPostProcessor implements ApplicationX.BeanPostProcessor {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            ApplicationX.BeanDefinition definition = applicationX.getBeanDefinition(beanName);
            // 检查是否是单例且不是NettyProperties
            if (definition != null && definition.isSingleton() && !(bean instanceof NettyProperties)) {
                nettyPropertiesSupplier.get().getApplication()
                        .addSingletonBean(bean, beanName, false);
            }
            return bean;
        }
    }

    /**
     * 用于在ApplicationX初始化时自动调用的静态方法
     */
    public static void register(ApplicationX applicationX) {
        // 查找带有@EnableNettyRpcClients注解的配置类
        List<Object> configBeans = applicationX.getBeanForAnnotation(EnableNettyRpcClients.class);
        for (Object configBean : configBeans) {
            NettyRpcClientBeanDefinitionRegistrar registrar = new NettyRpcClientBeanDefinitionRegistrar();
            registrar.registerRpcClients(applicationX, configBean.getClass());
        }
    }
}