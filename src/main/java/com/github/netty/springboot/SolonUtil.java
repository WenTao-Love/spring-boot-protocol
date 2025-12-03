package com.github.netty.springboot;

import com.github.netty.core.util.IOUtil;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.util.ResourceUtil;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * Solon框架工具类，替代SpringUtil
 */
public class SolonUtil {

    /**
     * 从Solon容器获取指定类型的bean
     * @param context Solon应用上下文
     * @param requiredType 需要获取的bean类型
     * @param <T> bean类型泛型
     * @return bean实例，如果不存在则返回null
     */
    public static <T> T getBean(AppContext context, Class<T> requiredType) {
        try {
            return context.getBean(requiredType);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查一个bean是否存在并且是单例的
     * Solon默认都是单例的，这里简单实现
     * @param context Solon应用上下文
     * @param beanName bean名称
     * @return 是否存在且为单例
     */
    public static boolean isSingletonBean(AppContext context, String beanName) {
        // Solon中默认所有bean都是单例的，这里简化实现
        try {
            // 尝试获取bean，如果抛出异常则表示不存在
            context.getBean(beanName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通过反射获取对象的字节数
     * @param object 目标对象
     * @param methodName 方法名
     * @return 字节数
     * @throws InvocationTargetException 反射调用异常
     * @throws IllegalAccessException 访问权限异常
     */
    public static Number getNumberBytes(Object object, String methodName) throws InvocationTargetException, IllegalAccessException {
        Method method = object.getClass().getMethod(methodName);
        Object value = method.invoke(object);
        if (!(value instanceof Number)) {
            Method toBytesMethod = value.getClass().getMethod("toBytes");
            value = toBytesMethod.invoke(value);
        }
        return (Number) value;
    }

    /**
     * 创建SSL上下文
     * @param ssl SSL配置
     * @param sslStoreProvider SSL存储提供者
     * @return SSL上下文构建器
     */
    public static SslContextBuilder newSslContext(Ssl ssl, SslBundle sslStoreProvider) {
        SslContextBuilder builder = SslContextBuilder.forServer(getKeyManagerFactory(ssl, sslStoreProvider))
                .trustManager(getTrustManagerFactory(ssl, sslStoreProvider));
        if (ssl.getEnabledProtocols() != null) {
            builder.protocols(ssl.getEnabledProtocols());
        }
        if (ssl.getCiphers() != null) {
            builder.ciphers(Arrays.asList(ssl.getCiphers()));
        }
        if (ssl.getClientAuth() == Ssl.ClientAuth.NEED) {
            builder.clientAuth(ClientAuth.REQUIRE);
        } else if (ssl.getClientAuth() == Ssl.ClientAuth.WANT) {
            builder.clientAuth(ClientAuth.OPTIONAL);
        }
        return builder;
    }

    private static KeyManagerFactory getKeyManagerFactory(Ssl ssl, SslBundle sslStoreProvider) {
        try {
            KeyStore keyStore = getKeyStore(ssl, sslStoreProvider);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory
                    .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            char[] keyPassword = (ssl.getKeyPassword() != null) ? ssl.getKeyPassword().toCharArray() : null;
            if (keyPassword == null && ssl.getKeyStorePassword() != null) {
                keyPassword = getPassword(ssl.getKeyStorePassword()).toCharArray();
            }
            keyManagerFactory.init(keyStore, keyPassword);
            return keyManagerFactory;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String getPassword(String password) {
        if (password == null || password.isEmpty()) {
            return password;
        }
        try {
            URL url = ResourceUtil.getResource(password);
            return IOUtil.readInput(url.openStream(), "UTF-8");
        } catch (Exception e) {
            return password;
        }
    }

    private static KeyStore getKeyStore(Ssl ssl, SslBundle sslStoreProvider) throws Exception {
        if (sslStoreProvider != null) {
            return sslStoreProvider.getStores().getKeyStore();
        }
        return loadKeyStore(ssl.getKeyStoreType(), ssl.getKeyStoreProvider(), ssl.getKeyStore(),
                getPassword(ssl.getKeyStorePassword()));
    }

    private static TrustManagerFactory getTrustManagerFactory(Ssl ssl, SslBundle sslStoreProvider) {
        try {
            KeyStore store = getTrustStore(ssl, sslStoreProvider);
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(store);
            return trustManagerFactory;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static KeyStore getTrustStore(Ssl ssl, SslBundle sslStoreProvider) throws Exception {
        if (sslStoreProvider != null) {
            return sslStoreProvider.getStores().getTrustStore();
        }
        return loadTrustStore(ssl.getTrustStoreType(), ssl.getTrustStoreProvider(), ssl.getTrustStore(),
                ssl.getTrustStorePassword());
    }

    private static KeyStore loadKeyStore(String type, String provider, String resource, String password) throws Exception {
        return loadStore(type, provider, resource, password);
    }

    private static KeyStore loadTrustStore(String type, String provider, String resource, String password) throws Exception {
        if (resource == null) {
            return null;
        }
        return loadStore(type, provider, resource, password);
    }

    private static KeyStore loadStore(String type, String provider, String resource, String password) throws Exception {
        type = (type != null) ? type : "JKS";
        KeyStore store = (provider != null) ? KeyStore.getInstance(type, provider) : KeyStore.getInstance(type);
        try {
            URL url = ResourceUtil.getResource(resource);
            store.load(url.openStream(), (password != null) ? password.toCharArray() : null);
            return store;
        } catch (Exception ex) {
            // 使用标准Java异常替代不存在的WrappingException
            throw new RuntimeException("Could not load key store '" + resource + "'", ex);
        }
    }

    /**
     * Solon的SSL配置类，替代Spring的Ssl类
     */
    public static class Ssl {
        public enum ClientAuth {
            NONE,
            NEED,
            WANT
        }

        private String keyStore;
        private String keyStorePassword;
        private String keyStoreType;
        private String keyStoreProvider;
        private String keyPassword;
        private String trustStore;
        private String trustStorePassword;
        private String trustStoreType;
        private String trustStoreProvider;
        private String[] enabledProtocols;
        private String[] ciphers;
        private ClientAuth clientAuth = ClientAuth.NONE;

        // Getters and setters
        public String getKeyStore() { return keyStore; }
        public void setKeyStore(String keyStore) { this.keyStore = keyStore; }
        public String getKeyStorePassword() { return keyStorePassword; }
        public void setKeyStorePassword(String keyStorePassword) { this.keyStorePassword = keyStorePassword; }
        public String getKeyStoreType() { return keyStoreType; }
        public void setKeyStoreType(String keyStoreType) { this.keyStoreType = keyStoreType; }
        public String getKeyStoreProvider() { return keyStoreProvider; }
        public void setKeyStoreProvider(String keyStoreProvider) { this.keyStoreProvider = keyStoreProvider; }
        public String getKeyPassword() { return keyPassword; }
        public void setKeyPassword(String keyPassword) { this.keyPassword = keyPassword; }
        public String getTrustStore() { return trustStore; }
        public void setTrustStore(String trustStore) { this.trustStore = trustStore; }
        public String getTrustStorePassword() { return trustStorePassword; }
        public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }
        public String getTrustStoreType() { return trustStoreType; }
        public void setTrustStoreType(String trustStoreType) { this.trustStoreType = trustStoreType; }
        public String getTrustStoreProvider() { return trustStoreProvider; }
        public void setTrustStoreProvider(String trustStoreProvider) { this.trustStoreProvider = trustStoreProvider; }
        public String[] getEnabledProtocols() { return enabledProtocols; }
        public void setEnabledProtocols(String[] enabledProtocols) { this.enabledProtocols = enabledProtocols; }
        public String[] getCiphers() { return ciphers; }
        public void setCiphers(String[] ciphers) { this.ciphers = ciphers; }
        public ClientAuth getClientAuth() { return clientAuth; }
        public void setClientAuth(ClientAuth clientAuth) { this.clientAuth = clientAuth; }
    }

    /**
     * Solon的SSL Bundle类，替代Spring的SslBundle类
     */
    public static class SslBundle {
        private SslStores stores;

        public SslStores getStores() {
            return stores;
        }

        public static class SslStores {
            private KeyStore keyStore;
            private KeyStore trustStore;

            public KeyStore getKeyStore() { return keyStore; }
            public void setKeyStore(KeyStore keyStore) { this.keyStore = keyStore; }
            public KeyStore getTrustStore() { return trustStore; }
            public void setTrustStore(KeyStore trustStore) { this.trustStore = trustStore; }
        }
    }
}