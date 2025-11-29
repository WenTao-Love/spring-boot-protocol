package com.github.netty.springboot.server;

import com.github.netty.core.util.ApplicationX;
import com.github.netty.protocol.servlet.util.InstanceFactory;

public class ApplicationInstanceFactory implements InstanceFactory {
    private final ClassLoader classLoader;
    private final ApplicationX application;

    public ApplicationInstanceFactory(ClassLoader classLoader, ApplicationX application) {
        this.classLoader = classLoader;
        this.application = application;
    }

    @Override
    public Object newInstance(String className) throws ReflectiveOperationException {
        Class<?> loadClass = classLoader.loadClass(className);
        return newInstance(loadClass);
    }

    @Override
    public <T> T newInstance(Class<T> clazz) throws ReflectiveOperationException {
        ApplicationX.BeanDefinition[] definitions = application.getBeanDefinitions(clazz);
        if (definitions.length == 0) {
            ApplicationX.BeanDefinition def = new ApplicationX.BeanDefinition();
            def.setBeanClass(clazz);
            application.addBeanDefinition(clazz.getName(), def);
        }
        return application.getBean(clazz);
    }
}
