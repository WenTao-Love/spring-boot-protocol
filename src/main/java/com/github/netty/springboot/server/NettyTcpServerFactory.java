package com.github.netty.springboot.server;

import com.github.netty.core.ProtocolHandler;
import com.github.netty.core.ServerListener;
import com.github.netty.core.util.IOUtil;
import com.github.netty.protocol.DynamicProtocolChannelHandler;
import com.github.netty.protocol.servlet.*;
import com.github.netty.springboot.NettyProperties;
import org.noear.solon.Solon;
import org.noear.solon.core.AppClassLoader;
import org.noear.solon.core.AppContext;
import org.noear.solon.core.Plugin;

import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * The netty TCP server factory (Solon)
 *
 * @author wangzihao
 */
public class NettyTcpServerFactory implements Plugin {
    private final NettyProperties nettyProperties;
    private final Supplier<DynamicProtocolChannelHandler> handlerSupplier;
    private final List<ProtocolHandler> protocolHandlers = new CopyOnWriteArrayList<>();
    private final List<ServerListener> serverListeners = new CopyOnWriteArrayList<>();
    private NettyTcpServer nettyTcpServer;
    private int port = 8080;
    private String contextPath = "";
    private int sessionTimeout;

    /**
     * Constructor
     *
     * @param nettyProperties nettyProperties
     * @param handlerSupplier handlerSupplier
     */
    public NettyTcpServerFactory(NettyProperties nettyProperties, Supplier<DynamicProtocolChannelHandler> handlerSupplier) {
        this.nettyProperties = nettyProperties;
        this.handlerSupplier = handlerSupplier;
    }
    
    @Override
    public void start(AppContext app) {
        // 初始化时自动创建并启动服务器
        if (nettyProperties.getTcp().isEnable()) {
            try {
                NettyTcpServer webServer = getWebServer();
                webServer.start();
            } catch (Exception e) {
                throw new RuntimeException("Failed to start Netty server", e);
            }
        }
    }
    
    @Override
    public void stop() {
        // 停止服务器
        if (nettyTcpServer != null) {
            nettyTcpServer.stop();
        }
    }

    public static InetSocketAddress getServerSocketAddress(InetAddress address, int port) {
        if (address == null) {
            try {
                address = InetAddress.getByAddress(new byte[]{0, 0, 0, 0});
                if (!address.isAnyLocalAddress()) {
                    address = InetAddress.getByName("::1");
                }
                if (!address.isAnyLocalAddress()) {
                    address = new InetSocketAddress(port).getAddress();
                }
            } catch (UnknownHostException e) {
                address = new InetSocketAddress(port).getAddress();
            }
        }
        return new InetSocketAddress(address, port);
    }

    /**
     * Get a new WebServer instance.
     *
     * @return a new web server instance
     */
    public NettyTcpServer getWebServer() {
        // Initialize the nettyTcpServer
        nettyTcpServer = new NettyTcpServer(nettyProperties, handlerSupplier, protocolHandlers, serverListeners);

        // Return the nettyTcpServer
        return nettyTcpServer;
    }

    /**
     * Set server port
     */
    public void setPort(int port) {
        this.port = port;
        if (nettyTcpServer != null) {
            // 可以在这里添加端口更新逻辑
        }
    }

    /**
     * Set context path
     */
    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    /**
     * Set session timeout
     */
    public void setSessionTimeout(int sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
    }
    
    /**
     * Set bind address
     */
    public void setAddress(java.net.InetAddress address) {
        // 设置绑定地址的逻辑
        if (nettyTcpServer != null) {
            // 可以在这里添加地址更新逻辑
        }
    }
    
    /**
     * Get the class loader
     *
     * @return the class loader
     */
    public ClassLoader getClassLoader() {
        return AppClassLoader.global();
    }
    
    /**
     * Add a protocol handler
     *
     * @param protocolHandler protocolHandler
     */
    public void addProtocolHandler(ProtocolHandler protocolHandler) {
        protocolHandlers.add(protocolHandler);
    }

    /**
     * Add a server listener
     *
     * @param serverListener serverListener
     */
    public void addServerListener(ServerListener serverListener) {
        serverListeners.add(serverListener);
    }
    
    /**
     * Get the protocol handlers
     *
     * @return protocolHandlers
     */
    public List<ProtocolHandler> getProtocolHandlers() {
        return protocolHandlers;
    }

    /**
     * Get the server listeners
     *
     * @return serverListeners
     */
    public List<ServerListener> getServerListeners() {
        return serverListeners;
    }

    /**
     * Get the netty properties
     *
     * @return nettyProperties
     */
    public NettyProperties getNettyProperties() {
        return nettyProperties;
    }
    
    /**
     * WebServer interface for Solon
     */
    public interface WebServer {
        // Solon WebServer interface
    }
}