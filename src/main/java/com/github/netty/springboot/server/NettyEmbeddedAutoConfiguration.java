package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.core.util.AbortPolicyWithReport;
import com.github.netty.core.util.NettyThreadPoolExecutor;
import com.github.netty.protocol.*;
import com.github.netty.protocol.dubbo.Application;
import com.github.netty.protocol.dubbo.ProxyFrontendHandler;
import com.github.netty.protocol.mqtt.interception.InterceptHandler;
import com.github.netty.protocol.mysql.client.MysqlFrontendBusinessHandler;
import com.github.netty.protocol.mysql.listener.MysqlPacketListener;
import com.github.netty.protocol.mysql.listener.WriterLogFilePacketListener;
import com.github.netty.protocol.mysql.server.MysqlBackendBusinessHandler;
import com.github.netty.protocol.servlet.util.HttpAbortPolicyWithReport;
import com.github.netty.springboot.NettyProperties;
import org.noear.solon.Solon;
import org.noear.solon.SolonApp;
import org.noear.solon.annotation.*;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.bean.LifecycleBean;
import org.noear.solon.core.util.BeanUtil;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * The netty container is automatically configured (Solon)
 *
 * @author wangzihao
 */
public class NettyEmbeddedAutoConfiguration implements Plugin {
    @Inject
    private AppContext appContext;
    @Inject
    private NettyProperties nettyProperties;
    
    @Override
    public void start(SolonApp app) {
        // 注册TCP服务工厂
        Collection<ProtocolHandler> protocolHandlers = appContext.getBeansOfType(ProtocolHandler.class);
        Collection<ServerListener> serverListeners = appContext.getBeansOfType(ServerListener.class);
        
        NettyTcpServerFactory serverFactory = nettyTcpServerFactory(protocolHandlers, serverListeners);
        appContext.putBean("nettyServerFactory", serverFactory);
        
        // 注册HTTP协议
        if (!appContext.hasBean(HttpServletProtocol.class)) {
            HttpServletProtocol httpProtocol = httpServletProtocol();
            appContext.putBean("httpServletProtocol", httpProtocol);
        }
        
        // 注册Dubbo协议（如果启用）
        if (Solon.cfg().getBool("server.netty.dubbo.enabled", false)) {
            if (!appContext.hasBean(DubboProtocol.class)) {
                DubboProtocol dubboProtocol = dubboProtocol();
                appContext.putBean("dubboProtocol", dubboProtocol);
            }
        }
        
        // 注册RPC协议（如果启用）
        if (Solon.cfg().getBool("server.netty.nrpc.enabled", false)) {
            if (!appContext.hasBean(NRpcProtocol.class)) {
                try {
                    NRpcProtocol nRpcProtocol = nRpcProtocol();
                    appContext.putBean("nRpcProtocol", nRpcProtocol);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Failed to initialize NRpcProtocol", e);
                }
            }
        }
        
        // 注册MQTT协议（如果启用）
        if (Solon.cfg().getBool("server.netty.mqtt.enabled", false)) {
            if (!appContext.hasBean(MqttProtocol.class)) {
                Collection<InterceptHandler> interceptHandlers = appContext.getBeansOfType(InterceptHandler.class);
                MqttProtocol mqttProtocol = mqttProtocol(interceptHandlers);
                appContext.putBean("mqttProtocol", mqttProtocol);
            }
        }
        
        // 注册MySQL协议（如果启用）
        if (Solon.cfg().getBool("server.netty.mysql.enabled", false)) {
            // 注册MySQL日志监听器
            if (!appContext.hasBean(WriterLogFilePacketListener.class)) {
                WriterLogFilePacketListener logListener = mysqlWriterLogFilePacketListener();
                appContext.putBean("mysqlWriterLogFilePacketListener", logListener);
            }
            
            // 注册MySQL协议
            if (!appContext.hasBean(MysqlProtocol.class)) {
                Collection<MysqlPacketListener> mysqlPacketListeners = appContext.getBeansOfType(MysqlPacketListener.class);
                MysqlProtocol mysqlProtocol = mysqlServerProtocol(mysqlPacketListeners);
                appContext.putBean("mysqlProtocol", mysqlProtocol);
            }
        }
    }
    
    @Override
    public void stop() {
        // Plugin shutdown logic
        // 可以在这里添加资源清理代码
    }

    /**
     * Add a TCP service factory
     */
    public NettyTcpServerFactory nettyTcpServerFactory(
            Collection<ProtocolHandler> protocolHandlers,
            Collection<ServerListener> serverListeners) {
        Supplier<DynamicProtocolChannelHandler> handlerSupplier = () -> {
            Class<? extends DynamicProtocolChannelHandler> type = nettyProperties.getChannelHandler();
            return type == DynamicProtocolChannelHandler.class ?
                    new DynamicProtocolChannelHandler() : appContext.getBean(type);
        };
        NettyTcpServerFactory tcpServerFactory = new NettyTcpServerFactory(nettyProperties, handlerSupplier);
        if (protocolHandlers != null) {
            tcpServerFactory.getProtocolHandlers().addAll(protocolHandlers);
        }
        if (serverListeners != null) {
            tcpServerFactory.getServerListeners().addAll(serverListeners);
        }
        return tcpServerFactory;
    }

    /**
     * Add the Dubbo protocol registry
     */
    public DubboProtocol dubboProtocol() {
        Supplier<ProxyFrontendHandler> proxySupplier = () -> {
            List<Application> applicationList = convert(nettyProperties.getDubbo().getRoutes());
            return new ProxyFrontendHandler(applicationList);
        };
        return new DubboProtocol(proxySupplier);
    }

    protected List<Application> convert(NettyProperties.Dubbo.ApplicationRoute[] routes) {
        List<Application> applicationList = new ArrayList<>();
        if (routes != null) {
            for (NettyProperties.Dubbo.ApplicationRoute source : routes) {
                String[] address = source.getAddress().split(":", 2);

                Application target = new Application();
                target.setName(source.getApplicationName());
                target.setAddress(new InetSocketAddress(address[0], Integer.parseInt(address[1])));
                target.setAttachmentApplicationName(source.getAttachmentName());
                target.setPathPatterns(source.getPathPatterns());
                target.setDefaultApplication(source.isDefaultApplication());
                applicationList.add(target);
            }
        }
        return applicationList;
    }

    /**
     * Add the RPC protocol registry
     */
    public NRpcProtocol nRpcProtocol() throws ClassNotFoundException {
        // Preheat codec
        Class.forName("com.github.netty.protocol.nrpc.codec.DataCodecUtil");

        NRpcProtocolSolonAdapter protocol = new NRpcProtocolSolonAdapter(appContext, nettyProperties, null);
        protocol.setMessageMaxLength(nettyProperties.getNrpc().getServerMessageMaxLength());
        protocol.setMethodOverwriteCheck(nettyProperties.getNrpc().isServerMethodOverwriteCheck());
        protocol.setServerDefaultVersion(nettyProperties.getNrpc().getServerDefaultVersion());
        protocol.setExecutorSupplier(newExecutorSupplier(nettyProperties.getNrpc().getThreadPool()));
        return protocol;
    }

    /**
     * Add the HTTP protocol registry
     */
    public HttpServletProtocol httpServletProtocol() {
        NettyProperties.HttpServlet http = nettyProperties.getHttpServlet();
        Supplier<Executor> executorSupplier = newExecutorSupplier(http.getThreadPool());
        Supplier<Executor> defaultExecutorSupplier = newDefaultExecutorSupplier(http.getThreadPool());

        HttpServletProtocolSolonAdapter protocol = new HttpServletProtocolSolonAdapter(
                nettyProperties, Solon.global().classLoader());
        
        if (executorSupplier != null) {
            protocol.setExecutorSupplier(executorSupplier);
        }
        if (defaultExecutorSupplier != null) {
            protocol.setDefaultExecutorSupplier(defaultExecutorSupplier);
        }
        
        protocol.setMaxInitialLineLength(http.getRequestMaxHeaderLineSize());
        protocol.setMaxHeaderSize(http.getRequestMaxHeaderSize());
        protocol.setMaxContentLength(http.getRequestMaxContentSize());
        protocol.setMaxBufferBytes(http.getResponseMaxBufferSize());
        protocol.setAutoFlushIdleMs(http.getAutoFlushIdleMs());
        
        return protocol;
    }

    /**
     * Add the MQTT protocol registry
     */
    public MqttProtocol mqttProtocol(Collection<InterceptHandler> interceptHandlers) {
        NettyProperties.Mqtt mqtt = nettyProperties.getMqtt();
        MqttProtocol protocol = new MqttProtocol(mqtt.getMessageMaxLength(), mqtt.getNettyReaderIdleTimeSeconds(), mqtt.getAutoFlushIdleMs());
        if (interceptHandlers != null) {
            interceptHandlers.forEach(protocol::addInterceptHandler);
        }
        return protocol;
    }

    /**
     * Add the MYSQL protocol registry
     */
    public MysqlProtocol mysqlServerProtocol(Collection<MysqlPacketListener> mysqlPacketListeners) {
        NettyProperties.Mysql mysql = nettyProperties.getMysql();
        MysqlProtocol protocol = new MysqlProtocol(new InetSocketAddress(mysql.getMysqlHost(), mysql.getMysqlPort()));
        protocol.setMaxPacketSize(mysql.getPacketMaxLength());
        if (mysqlPacketListeners != null) {
            protocol.getMysqlPacketListeners().addAll(mysqlPacketListeners);
        }
        
        if (mysql.getFrontendBusinessHandler() != MysqlFrontendBusinessHandler.class) {
            protocol.setFrontendBusinessHandler(() -> BeanUtil.newInstance(mysql.getFrontendBusinessHandler()));
        }

        if (mysql.getBackendBusinessHandler() != MysqlBackendBusinessHandler.class) {
            protocol.setBackendBusinessHandler(() -> BeanUtil.newInstance(mysql.getBackendBusinessHandler()));
        }
        return protocol;
    }

    /**
     * mysql proxy WriterLogFilePacketListener
     */
    public WriterLogFilePacketListener mysqlWriterLogFilePacketListener() {
        NettyProperties.Mysql mysql = nettyProperties.getMysql();
        WriterLogFilePacketListener listener = new WriterLogFilePacketListener();
        listener.setEnable(mysql.getProxyLog().isEnable());
        listener.setLogFileName(Solon.cfg().getProperty(mysql.getProxyLog().getLogFileName()));
        listener.setLogPath(Solon.cfg().getProperty(mysql.getProxyLog().getLogPath()));
        listener.setLogWriteInterval(mysql.getProxyLog().getLogFlushInterval());
        return listener;
    }

    protected Supplier<Executor> newExecutorSupplier(NettyProperties.HttpServlet.ServerThreadPool pool) {
        Supplier<Executor> executorSupplier;
        if (pool.isEnable()) {
            if (pool.getExecutor() == NettyThreadPoolExecutor.class) {
                RejectedExecutionHandler rejectedHandler;
                if (pool.getRejected() == HttpAbortPolicyWithReport.class) {
                    rejectedHandler = new HttpAbortPolicyWithReport(pool.getPoolName(), pool.getDumpPath(), "HttpServlet");
                } else {
                    rejectedHandler = BeanUtil.newInstance(pool.getRejected());
                }

                String poolName = pool.getPoolName();
                int coreThreads = pool.getCoreThreads();
                int maxThreads = pool.getMaxThreads();
                int queues = pool.getQueues();
                int keepAliveSeconds = pool.getKeepAliveSeconds();
                boolean allowCoreThreadTimeOut = pool.isAllowCoreThreadTimeOut();
                NettyThreadPoolExecutor executor = newNettyThreadPoolExecutor(poolName, coreThreads, maxThreads, queues, keepAliveSeconds, allowCoreThreadTimeOut, rejectedHandler);
                executorSupplier = () -> executor;
            } else {
                Executor executor = BeanUtil.newInstance(pool.getExecutor());
                executorSupplier = () -> executor;
            }
        } else {
            executorSupplier = () -> null;
        }
        return executorSupplier;
    }

    protected Supplier<Executor> newDefaultExecutorSupplier(NettyProperties.HttpServlet.ServerThreadPool pool) {
        RejectedExecutionHandler rejectedHandler;
        if (pool.getRejected() == HttpAbortPolicyWithReport.class) {
            rejectedHandler = new HttpAbortPolicyWithReport(pool.getPoolName(), pool.getDumpPath(), "Default Pool HttpServlet");
        } else {
            rejectedHandler = BeanUtil.newInstance(pool.getRejected());
        }
        return new LazyPool(this, pool, rejectedHandler);
    }

    protected Supplier<Executor> newExecutorSupplier(NettyProperties.Nrpc.ServerThreadPool pool) {
        Supplier<Executor> executorSupplier;
        if (pool.isEnable()) {
            if (pool.getExecutor() == NettyThreadPoolExecutor.class) {
                RejectedExecutionHandler rejectedHandler;
                if (pool.getRejected() == AbortPolicyWithReport.class) {
                    rejectedHandler = new AbortPolicyWithReport(pool.getPoolName(), pool.getDumpPath(), "Nrpc");
                } else {
                    rejectedHandler = BeanUtil.newInstance(pool.getRejected());
                }

                String poolName = pool.getPoolName();
                int coreThreads = pool.getCoreThreads();
                int maxThreads = pool.getMaxThreads();
                int queues = pool.getQueues();
                int keepAliveSeconds = pool.getKeepAliveSeconds();
                boolean allowCoreThreadTimeOut = pool.isAllowCoreThreadTimeOut();
                NettyThreadPoolExecutor executor = newNettyThreadPoolExecutor(poolName, coreThreads, maxThreads, queues, keepAliveSeconds, allowCoreThreadTimeOut, rejectedHandler);
                executorSupplier = () -> executor;
            } else {
                executorSupplier = () -> BeanUtil.newInstance(pool.getExecutor());
            }
        } else {
            executorSupplier = () -> null;
        }
        return executorSupplier;
    }

    protected NettyThreadPoolExecutor newNettyThreadPoolExecutor(
            String poolName,
            int coreThreads,
            int maxThreads,
            int queues,
            int keepAliveSeconds,
            boolean allowCoreThreadTimeOut,
            RejectedExecutionHandler handler) {
        BlockingQueue<Runnable> workQueue = queues == 0 ?
                new SynchronousQueue<>() :
                (queues < 0 ? new LinkedBlockingQueue<>(Integer.MAX_VALUE)
                        : new LinkedBlockingQueue<>(queues));
        boolean daemon = true;
        NettyThreadPoolExecutor executor = new NettyThreadPoolExecutor(
                coreThreads, maxThreads, keepAliveSeconds, TimeUnit.SECONDS,
                workQueue, poolName, Thread.NORM_PRIORITY, daemon, handler);
        executor.allowCoreThreadTimeOut(allowCoreThreadTimeOut);
        return executor;
    }

    public static class LazyPool implements Supplier<Executor> {
        protected final NettyProperties.HttpServlet.ServerThreadPool pool;
        protected final RejectedExecutionHandler rejectedHandler;
        protected final NettyEmbeddedAutoConfiguration autoConfiguration;
        protected volatile Executor executor;

        public LazyPool(NettyEmbeddedAutoConfiguration autoConfiguration, NettyProperties.HttpServlet.ServerThreadPool pool, RejectedExecutionHandler rejectedHandler) {
            this.autoConfiguration = autoConfiguration;
            this.pool = pool;
            this.rejectedHandler = rejectedHandler;
        }

        @Override
        public Executor get() {
            if (executor == null) {
                synchronized (this) {
                    if (executor == null) {
                        String poolName = pool.getPoolName();
                        int coreThreads = pool.getCoreThreads();
                        int maxThreads = pool.getMaxThreads();
                        int queues = pool.getQueues();
                        int keepAliveSeconds = pool.getKeepAliveSeconds();
                        boolean allowCoreThreadTimeOut = pool.isAllowCoreThreadTimeOut();
                        executor = autoConfiguration.newNettyThreadPoolExecutor(poolName, coreThreads, maxThreads, queues, keepAliveSeconds, allowCoreThreadTimeOut, rejectedHandler);
                    }
                }
            }
            return executor;
        }
    }

}