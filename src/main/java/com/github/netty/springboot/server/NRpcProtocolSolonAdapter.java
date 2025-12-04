package com.github.netty.springboot.server;

import com.github.netty.annotation.NRpcParam;
import com.github.netty.annotation.NRpcService;
import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.util.*;
import com.github.netty.protocol.NRpcProtocol;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.annotation.AnnotationUtil;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Function;

/**
 * Internal RPC protocol registry (solon adapter)
 *
 * @author wangzihao
 */
public class NRpcProtocolSolonAdapter extends NRpcProtocol {
    private final ClassFileMethodToParameterNamesFunction classFileMethodToParameterNamesFunction = new ClassFileMethodToParameterNamesFunction();
    private final AnnotationMethodToParameterNamesFunction annotationMethodToParameterNamesFunction = new AnnotationMethodToParameterNamesFunction(
            NRpcParam.class);
    private final AppContext appContext;

    public NRpcProtocolSolonAdapter(AppContext appContext, ApplicationX application) {
        super(application);
        this.appContext = appContext;
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {
        Collection list = super.getApplication().getBeanForAnnotation(NRpcService.class);

        for (Object serviceImpl : list) {
            if (super.existInstance(serviceImpl)) {
                continue;
            }

            String beanName = getBeanName(serviceImpl);
            Function<Method, String[]> methodToParameterNamesFunction = getMethodToParameterNamesFunction(serviceImpl);
            
            if (StringUtil.isNotEmpty(beanName)) {
                super.addInstance(serviceImpl, beanName, methodToParameterNamesFunction);
            } else {
                super.addInstance(serviceImpl);
            }
        }
        super.onServerStart(server);
    }

    // Solon doesn't use BeanPostProcessor directly
    public void registerBeans() {
        // Solon will automatically handle singleton beans
    }

    protected Function<Method, String[]> getMethodToParameterNamesFunction(Object serviceImpl) {
        if (ReflectUtil.hasParameterAnnotation(serviceImpl.getClass(), annotationMethodToParameterNamesFunction.getParameterAnnotationClasses())) {
            return annotationMethodToParameterNamesFunction;
        } else {
            return classFileMethodToParameterNamesFunction;
        }
    }

    protected String getBeanName(Object serviceImpl) {
        // In Solon, we can use the bean name directly or a custom annotation value
        NRpcService service = AnnotationUtil.getAnnotation(serviceImpl.getClass(), NRpcService.class);
        if (service != null && StringUtil.isNotEmpty(service.name())) {
            return service.name();
        }
        return null;
    }

    public AnnotationMethodToParameterNamesFunction getAnnotationMethodToParameterNamesFunction() {
        return annotationMethodToParameterNamesFunction;
    }
}