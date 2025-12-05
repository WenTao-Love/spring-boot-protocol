package com.github.netty.solon.client;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.apache.dubbo.rpc.protocol.tri.rest.mapping.RequestMapping;
import org.apache.dubbo.rpc.protocol.tri.rest.openapi.model.RequestBody;
import org.noear.solon.annotation.Body;
import org.noear.solon.annotation.Cookie;
import org.noear.solon.annotation.Header;
import org.noear.solon.annotation.Mapping;
import org.noear.solon.annotation.Multipart;
import org.noear.solon.annotation.Param;
import org.noear.solon.annotation.Path;

import com.github.netty.annotation.NRpcMethod;
import com.github.netty.annotation.NRpcParam;
import com.github.netty.core.Ordered;
import com.github.netty.core.util.AnnotationMethodToMethodNameFunction;
import com.github.netty.core.util.AnnotationMethodToParameterNamesFunction;
import com.github.netty.core.util.ApplicationX;
import com.github.netty.core.util.Recyclable;
import com.github.netty.core.util.ReflectUtil;
import com.github.netty.core.util.StringUtil;
import com.github.netty.protocol.nrpc.RpcClient;
import com.github.netty.protocol.nrpc.RpcClientAop;
import com.github.netty.protocol.nrpc.RpcMethod;
import com.github.netty.protocol.nrpc.RpcServerChannelHandler;
import com.github.netty.protocol.nrpc.RpcServerInstance;
import com.github.netty.protocol.nrpc.exception.RpcConnectException;
import com.github.netty.springboot.NettyProperties;

import io.netty.util.concurrent.FastThreadLocal;

/**
 * RPC client proxy (thread safe)
 * 1. Management rpc client different ip addresses. Ip address corresponds to a client only.
 * 2. In selecting ip address, will call NettyRpcLoadBalanced.class.
 *
 * @author wangzihao
 * @see com.github.netty.springboot.client.NettyRpcLoadBalanced#chooseAddress(NettyRpcRequest)
 * @see com.github.netty.protocol.nrpc.RpcClient
 * 
 * -----------------------------------------------------------------------
 * 
 * Support rpc method annotation list.
 * @see NRpcParam
 * @see RequestMapping
 * @see RequestParam
 * @see RequestBody
 * @see RequestHeader
 * @see PathVariable
 * @see CookieValue
 * @see RequestPart
 */
public class NettyRpcClientProxy implements InvocationHandler {
    private static final Map<InetSocketAddress, RpcClient> CLIENT_MAP = new ConcurrentHashMap<>(64);
    private static final FastThreadLocal<DefaultNettyRpcRequest> REQUEST_THREAD_LOCAL = new FastThreadLocal<DefaultNettyRpcRequest>() {
        @Override
        protected DefaultNettyRpcRequest initialValue() throws Exception {
            return new DefaultNettyRpcRequest();
        }
    };
    private static final FastThreadLocal<NettyRpcFilterChain> FILTER_CHAIN_THREAD_LOCAL = new FastThreadLocal<NettyRpcFilterChain>() {
        @Override
        protected NettyRpcFilterChain initialValue() throws Exception {
            return new NettyRpcFilterChain();
        }
    };
    private final String requestMappingName;
    private final Class<?> interfaceClass;
    private final String rpcInstanceKey;
    private final String version;
    private final AnnotationMethodToParameterNamesFunction annotationMethodToParameterNamesFunction = new AnnotationMethodToParameterNamesFunction(
            NRpcParam.class, Param.class, Body.class, Header.class,
            Path.class, Cookie.class, Multipart.class);
    private final AnnotationMethodToMethodNameFunction annotationMethodToMethodNameFunction = new AnnotationMethodToMethodNameFunction(
            NRpcMethod.class, Mapping.class);
    private String serviceName;
    private int timeout;
    private final NettyProperties properties;
    private Supplier<NettyRpcLoadBalanced> loadBalancedSupplier;

    NettyRpcClientProxy(String serviceName, String requestMappingName, Class interfaceClass, NettyProperties properties, Supplier<NettyRpcLoadBalanced> loadBalancedSupplier) {
        this.serviceName = serviceName;
        this.interfaceClass = interfaceClass;
        this.properties = properties;
        this.loadBalancedSupplier = loadBalancedSupplier;
        this.requestMappingName = StringUtil.isEmpty(requestMappingName) ? getRequestMappingName(interfaceClass) : requestMappingName;
        this.version = RpcServerInstance.getVersion(interfaceClass, properties.getNrpc().getClientDefaultVersion());
        this.rpcInstanceKey = RpcClient.getClientInstanceKey(interfaceClass, this.requestMappingName, version);
        this.timeout = properties.getNrpc().getClientServerResponseTimeout();
    }

    public static NettyRpcRequest getRequest() {
        return REQUEST_THREAD_LOCAL.get();
    }

    public static NettyRpcFilter.FilterChain getFilterChain() {
        return FILTER_CHAIN_THREAD_LOCAL.get();
    }

    public static Map<InetSocketAddress, RpcClient> getClientMap() {
        return CLIENT_MAP;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        int parameterCount = method.getParameterCount();
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(this, args);
        }
        if ("toString".equals(methodName) && parameterCount == 0) {
            return this.toString();
        }
        if ("hashCode".equals(methodName) && parameterCount == 0) {
            return this.hashCode();
        }
        if ("equals".equals(methodName) && parameterCount == 1) {
            return this.equals(args[0]);
        }

        NettyRpcFilterChain filterChain = FILTER_CHAIN_THREAD_LOCAL.get();
        DefaultNettyRpcRequest request = REQUEST_THREAD_LOCAL.get();
        request.args = args;
        request.method = method;
        request.proxy = proxy;
        request.timeout = this.timeout;
        request.clientProxy = this;
        try {
            InetSocketAddress address = chooseAddress(request);
            request.remoteAddress = address;

            RpcClient rpcClient = getClient(address);
            request.rpcClient = rpcClient;

            RpcClient.Sender sender = rpcClient.getRpcInstance(rpcInstanceKey);
            if (sender == null) {
                sender = rpcClient.newRpcInstance(interfaceClass, timeout,
                        version, requestMappingName,
                        annotationMethodToParameterNamesFunction,
                        annotationMethodToMethodNameFunction,
                        properties.getNrpc().isClientMethodOverwriteCheck());
            }
            request.sender = sender;

            filterChain.nettyRpcFilterList = getNettyRpcFilterList();
            filterChain.doFilter(request);
            return request.getResponse();
        } finally {
            request.recycle();
            filterChain.recycle();
        }
    }

    public List<NettyRpcFilter> getNettyRpcFilterList() {
        // 使用Solon风格获取Bean列表
        List<NettyRpcFilter> nettyRpcFilterList = properties.getApplication().getBeanForType(NettyRpcFilter.class);
        // 创建一个适用于NettyRpcFilter的比较器
        nettyRpcFilterList.sort((filter1, filter2) -> {
            // 检查是否实现了Ordered接口
            if (filter1 instanceof Ordered && filter2 instanceof Ordered) {
                Ordered ordered1 = (Ordered) filter1;
                Ordered ordered2 = (Ordered) filter2;
                return ordered1.getOrder() < ordered2.getOrder() ? -1 : 1;
            }
            // 如果只有一个实现了Ordered接口，则优先
            else if (filter1 instanceof Ordered) {
                return -1;
            }
            else if (filter2 instanceof Ordered) {
                return 1;
            }
            // 都没有实现Ordered接口，则保持原有顺序
            return 0;
        });
        return nettyRpcFilterList;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        if (timeout > 0) {
            this.timeout = timeout;
        }
    }

    public AnnotationMethodToMethodNameFunction getAnnotationMethodToMethodNameFunction() {
        return annotationMethodToMethodNameFunction;
    }

    public AnnotationMethodToParameterNamesFunction getAnnotationMethodToParameterNamesFunction() {
        return annotationMethodToParameterNamesFunction;
    }

    public String getRequestMappingName(Class objectType) {
        String requestMappingName = RpcServerChannelHandler.getRequestMappingName(objectType);
        if (StringUtil.isNotEmpty(requestMappingName)) {
            return requestMappingName;
        }

        Mapping requestMapping = ReflectUtil.findAnnotation(objectType, Mapping.class);
        if (requestMapping != null) {
            requestMappingName = requestMapping.name();
            String value = requestMapping.value();
            String path = requestMapping.path();
            if (StringUtil.isEmpty(requestMappingName) && StringUtil.isNotEmpty(value)) {
                requestMappingName = value;
            }
            if (StringUtil.isEmpty(requestMappingName) && StringUtil.isNotEmpty(path)) {
                requestMappingName = path;
            }
            if (StringUtil.isNotEmpty(requestMappingName)) {
                return requestMappingName;
            }
        }

        requestMappingName = RpcServerChannelHandler.generateRequestMappingName(objectType);
        return requestMappingName;
    }

    /**
     * Get the RPC client (from the current thread, if not, create it automatically)
     *
     * @param address InetSocketAddress
     * @return RpcClient
     */
    public RpcClient getClient(InetSocketAddress address) {
        RpcClient rpcClient = CLIENT_MAP.get(address);
        if (rpcClient == null) {
            synchronized (CLIENT_MAP) {
                rpcClient = CLIENT_MAP.get(address);
                if (rpcClient == null) {
                    NettyProperties.Nrpc nrpc = properties.getNrpc();
                    rpcClient = new RpcClient(address);
                    // 使用Solon风格获取Bean列表
                    rpcClient.getAopList().addAll(properties.getApplication().getBeanForType(RpcClientAop.class));
                    rpcClient.setIoThreadCount(nrpc.getClientIoThreads());
                    rpcClient.setIoRatio(nrpc.getClientIoRatio());
                    rpcClient.setConnectTimeout(nrpc.getClientConnectTimeout());
                    rpcClient.setIdleTimeMs(nrpc.getClientHeartIntervalTimeMs());
                    rpcClient.setReconnectScheduledIntervalMs(nrpc.getClientReconnectScheduledIntervalMs());
                    rpcClient.setEnableRpcHeartLog(nrpc.isClientEnableHeartLog());
                    rpcClient.setEnableReconnectScheduledTask(nrpc.isClientReconnectScheduledTaskEnable());
                    CLIENT_MAP.put(address, rpcClient);
                }
            }
        }
        return rpcClient;
    }

    public InetSocketAddress chooseAddress(NettyRpcRequest request) {
        InetSocketAddress address;
        try {
            address = loadBalancedSupplier.get().chooseAddress(request);
        } catch (Exception e) {
            throw new RpcConnectException("Rpc Failed to select client address.  cause [" + e.getLocalizedMessage() + "]", e);
        }
        if (address == null) {
            throw new NullPointerException("Rpc Failed to select client address. cause [return address is null]");
        }
        return address;
    }

    public String getRequestMappingName() {
        return requestMappingName;
    }

    public String getVersion() {
        return version;
    }

    public Class<?> getInterfaceClass() {
        return interfaceClass;
    }

    public String getRpcInstanceKey() {
        return rpcInstanceKey;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public NettyProperties getProperties() {
        return properties;
    }

    public Supplier<NettyRpcLoadBalanced> getLoadBalancedSupplier() {
        return loadBalancedSupplier;
    }

    public void setLoadBalancedSupplier(Supplier<NettyRpcLoadBalanced> loadBalancedSupplier) {
        this.loadBalancedSupplier = loadBalancedSupplier;
    }

    @Override
    public String toString() {
        return "NettyRpcClientProxy{" +
                "serviceName='" + serviceName + '\'' +
                ", requestMappingName='" + requestMappingName + '\'' +
                ", interfaceClass=" + interfaceClass +
                ", version='" + version + '\'' +
                ", timeout=" + timeout +
                '}';
    }

    private static class NettyRpcFilterChain implements NettyRpcFilter.FilterChain, Recyclable {
        private List<NettyRpcFilter> nettyRpcFilterList;
        private int count = 0;

        @Override
        public void doFilter(NettyRpcFullRequest request) throws Throwable {
            if (count < nettyRpcFilterList.size()) {
                nettyRpcFilterList.get(count++).doFilter(request, this);
            }
        }

        @Override
        public List<NettyRpcFilter> getNettyRpcFilterList() {
            return Collections.unmodifiableList(nettyRpcFilterList);
        }

        @Override
        public void recycle() {
            count = 0;
            nettyRpcFilterList = null;
        }
    }

    /**
     * Default nett request (parameter [args array] can be modified)
     */
    private static class DefaultNettyRpcRequest implements NettyRpcFullRequest, Recyclable {
        private AtomicBoolean responseGetFlag = new AtomicBoolean(false);
        private Method method;
        private Object[] args;
        private NettyRpcClientProxy clientProxy;
        private int timeout;
        private Object proxy;
        private RpcClient rpcClient;
        private InetSocketAddress remoteAddress;
        private RpcClient.Sender sender;

        private volatile Object response;
        private volatile Throwable throwable;
        private volatile boolean doneFlag;

        @Override
        public String getRpcInstanceKey() {
            return clientProxy.rpcInstanceKey;
        }

        @Override
        public NettyRpcClientProxy getClientProxy() {
            return clientProxy;
        }

        @Override
        public Supplier<NettyRpcLoadBalanced> getLoadBalancedSupplier() {
            return clientProxy.loadBalancedSupplier;
        }

        @Override
        public Object getProxy() {
            return proxy;
        }

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArgs() {
            return args;
        }

        @Override
        public String getServiceName() {
            return clientProxy.serviceName;
        }

        @Override
        public String getRequestMappingName() {
            return clientProxy.requestMappingName;
        }

        @Override
        public String getVersion() {
            return clientProxy.version;
        }

        @Override
        public int getTimeout() {
            return timeout;
        }

        @Override
        public void setTimeout(int timeout) {
            this.timeout = timeout;
        }

        @Override
        public NettyProperties getNettyProperties() {
            return clientProxy.properties;
        }

        @Override
        public ApplicationX getApplication() {
            return clientProxy.properties.getApplication();
        }

        @Override
        public Map<InetSocketAddress, RpcClient> getClientMap() {
            return CLIENT_MAP;
        }

        @Override
        public Class getInterfaceClass() {
            return clientProxy.interfaceClass;
        }

        @Override
        public RpcClient getRpcClient() {
            return rpcClient;
        }

        @Override
        public RpcClient.Sender getSender() {
            return sender;
        }

        @Override
        public RpcMethod<RpcClient> getRpcMethod() {
            if (sender == null) {
                return null;
            }
            String rpcMethodName = RpcMethod.getMethodDescriptorName(method);
            return sender.getRpcMethodMap().get(rpcMethodName);
        }

        @Override
        public Map<String, RpcMethod<RpcClient>> getRpcMethodMap() {
            if (sender == null) {
                return null;
            }
            return sender.getRpcMethodMap();
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return remoteAddress;
        }

        @Override
        public Object getResponse() throws Throwable {
            if (sender == null) {
                return null;
            }
            if (responseGetFlag.compareAndSet(false, true)) {
                sender.setTimeout(timeout);
                try {
                    response = sender.invoke(getProxy(), getMethod(), getArgs());
                } catch (Throwable t) {
                    throwable = t;
                    throw t;
                } finally {
                    doneFlag = true;
                }
            } else if (doneFlag) {
                if (throwable != null) {
                    throw throwable;
                }
            } else {
                throw new ConcurrentModificationException("other thread call getting response!");
            }
            return response;
        }

        @Override
        public void recycle() {
            args = null;
            method = null;
            clientProxy = null;
            proxy = null;
            remoteAddress = null;
            rpcClient = null;
            sender = null;
            response = null;
            throwable = null;
            doneFlag = false;
            responseGetFlag.set(false);
        }
    }
}