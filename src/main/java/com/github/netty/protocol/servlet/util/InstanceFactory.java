package com.github.netty.protocol.servlet.util;

public interface InstanceFactory {
    InstanceFactory DEFAULT = new InstanceFactory() {
        @Override
        public Object newInstance(String className) throws ReflectiveOperationException {
            Class<?> clazz = Class.forName(className);
            return newInstance(clazz);
        }

        @Override
        public <T> T newInstance(Class<T> clazz) throws ReflectiveOperationException {
            return clazz.getDeclaredConstructor().newInstance();
        }
    };

    Object newInstance(String className) throws ReflectiveOperationException;

    <T> T newInstance(Class<T> clazz) throws ReflectiveOperationException;
}