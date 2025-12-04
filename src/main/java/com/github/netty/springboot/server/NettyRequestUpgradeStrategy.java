package com.github.netty.springboot.server;

import com.github.netty.protocol.servlet.DispatcherChannelHandler;
import com.github.netty.protocol.servlet.ServletHttpExchange;
import com.github.netty.protocol.servlet.ServletHttpServletRequest;
import com.github.netty.protocol.servlet.util.HttpHeaderConstants;
import com.github.netty.protocol.servlet.util.ServletUtil;
import com.github.netty.protocol.servlet.websocket.NettyMessageToWebSocketRunnable;
import com.github.netty.protocol.servlet.websocket.WebSocketServerContainer;
import com.github.netty.protocol.servlet.websocket.WebSocketServerHandshaker13Extension;
import com.github.netty.protocol.servlet.websocket.WebSocketSession;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.websocket.Endpoint;
import jakarta.websocket.Extension;
import jakarta.websocket.WebSocketContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import org.noear.solon.core.handle.Context;
import org.noear.solon.core.handle.Handler;
import org.noear.solon.core.util.LinkedCaseInsensitiveMap;
import org.noear.solon.core.util.LinkedMultiValueMap;
import org.noear.solon.core.util.MultiValueMap;

import java.security.Principal;
import java.util.*;

/**
 * Websocket version number: the version number of draft 8 to draft 12 is 8, and the version number of draft 13 and later is the same as the draft number (Solon)
 *
 * @author wangzihao
 */
public class NettyRequestUpgradeStrategy {
    private int maxFramePayloadLength;
    private static final String[] SUPPORTED_VERSIONS = new String[]{WebSocketVersion.V13.toHttpHeaderValue()};
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NettyRequestUpgradeStrategy.class);

    public NettyRequestUpgradeStrategy() {
        this(65536);
    }

    public NettyRequestUpgradeStrategy(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    public String[] getSupportedVersions() {
        return SUPPORTED_VERSIONS;
    }

    /**
     * Upgrade a request to WebSocket
     * 
     * @param context Solon context
     * @param selectedProtocol selected protocol
     * @param selectedExtensions selected extensions
     * @param endpoint WebSocket endpoint
     * @throws Exception if upgrade fails
     */
    public void upgradeInternal(Context context, String selectedProtocol,
                              List<Extension> selectedExtensions, Endpoint endpoint) throws Exception {
        HttpServletRequest servletRequest = getHttpServletRequest(context);
        ServletHttpServletRequest httpServletRequest = ServletUtil.unWrapper(servletRequest);
        if (httpServletRequest == null) {
            throw new RuntimeException(
                    "Servlet request failed to upgrade to WebSocket: " + servletRequest.getRequestURL());
        }

        WebSocketServerContainer serverContainer = getContainer(servletRequest);
        java.security.Principal principal = context.session() != null ? context.session().userPrincipal() : null;
        Map<String, String> pathParams = new LinkedHashMap<>(3);

        // Create endpoint configuration
        ServerEndpointConfig endpointConfig = ServerEndpointConfig.Builder
                .create(endpoint.getClass(), servletRequest.getRequestURI())
                .build();
        
        List<String> subprotocols = new ArrayList<>();
        subprotocols.add("*");
        if (selectedProtocol != null && !subprotocols.contains(selectedProtocol)) {
            subprotocols.add(selectedProtocol);
        }
        endpointConfig.setSubprotocols(subprotocols);
        if (selectedExtensions != null) {
            endpointConfig.setExtensions(selectedExtensions);
        }

        handshakeToWebsocket(httpServletRequest, selectedProtocol, maxFramePayloadLength, principal,
                selectedExtensions, pathParams, endpoint,
                endpointConfig, serverContainer);
    }

    /**
     * Get installed WebSocket extensions
     */
    public List<Extension> getInstalledExtensions(WebSocketContainer container) {
        return container.getInstalledExtensions();
    }

    /**
     * Get WebSocket container
     */
    protected WebSocketServerContainer getContainer(HttpServletRequest request) {
        return (WebSocketServerContainer) request.getServletContext().getAttribute(WebSocketContainer.class.getName());
    }
    
    /**
     * Get HttpServletRequest from Solon context
     */
    protected HttpServletRequest getHttpServletRequest(Context context) {
        // 从Solon上下文获取HttpServletRequest
        // 这里假设已经有相应的适配机制
        Object requestObj = context.request();
        if (requestObj instanceof HttpServletRequest) {
            return (HttpServletRequest) requestObj;
        }
        throw new RuntimeException("Cannot get HttpServletRequest from Solon context");
    }

    /**
     * The WebSocket handshake
     *
     * @param servletRequest        servletRequest
     * @param subprotocols          subprotocols
     * @param maxFramePayloadLength maxFramePayloadLength
     * @param userPrincipal         userPrincipal
     * @param negotiatedExtensions  negotiatedExtensions
     * @param pathParameters        pathParameters
     * @param localEndpoint         localEndpoint
     * @param endpointConfig        endpointConfig
     * @param webSocketContainer    webSocketContainer
     */
    protected void handshakeToWebsocket(ServletHttpServletRequest servletRequest, String subprotocols, int maxFramePayloadLength, Principal userPrincipal,
                                        List<Extension> negotiatedExtensions, Map<String, String> pathParameters,
                                        Endpoint localEndpoint, ServerEndpointConfig endpointConfig, WebSocketServerContainer webSocketContainer) {
        FullHttpRequest nettyRequest = convertFullHttpRequest(servletRequest);
        ServletHttpExchange exchange = servletRequest.getHttpExchange();
        exchange.setWebsocket(true);
        String queryString = servletRequest.getQueryString();
        String httpSessionId = servletRequest.getRequestedSessionId();
        String webSocketURL = getWebSocketLocation(servletRequest);
        Map<String, List<String>> requestParameterMap = getRequestParameterMap(servletRequest);

        WebSocketServerHandshaker13Extension wsHandshaker = new WebSocketServerHandshaker13Extension(webSocketURL, subprotocols, true, maxFramePayloadLength);
        ChannelFuture handshakelFuture = wsHandshaker.handshake(exchange.getChannelHandlerContext().channel(), nettyRequest);
        handshakelFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                DispatcherChannelHandler.setMessageToRunnable(channel, new NettyMessageToWebSocketRunnable(DispatcherChannelHandler.getMessageToRunnable(channel)));
                WebSocketSession websocketSession = new WebSocketSession(
                        channel, webSocketContainer, wsHandshaker,
                        requestParameterMap,
                        queryString, userPrincipal, httpSessionId,
                        negotiatedExtensions, pathParameters, localEndpoint, endpointConfig);

                WebSocketSession.setSession(channel, websocketSession);

                localEndpoint.onOpen(websocketSession, endpointConfig);
            } else {
                logger.warn("The Websocket handshake failed : " + webSocketURL, future.cause());
            }
        });
    }

    private FullHttpRequest convertFullHttpRequest(ServletHttpServletRequest request) {
        HttpRequest nettyRequest = request.getNettyRequest();
        if (nettyRequest instanceof FullHttpRequest) {
            return (FullHttpRequest) nettyRequest;
        }
        return new DefaultFullHttpRequest(nettyRequest.protocolVersion(), nettyRequest.method(), nettyRequest.uri(), Unpooled.buffer(0), nettyRequest.headers(), EmptyHttpHeaders.INSTANCE);
    }

    protected Map<String, List<String>> getRequestParameterMap(HttpServletRequest request) {
        MultiValueMap<String, String> requestParameterMap = new LinkedMultiValueMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            for (String value : entry.getValue()) {
                requestParameterMap.add(entry.getKey(), value);
            }
        }
        return requestParameterMap;
    }

    protected String getWebSocketLocation(HttpServletRequest req) {
        String host = req.getHeader(HttpHeaderConstants.HOST.toString());
        if (host == null || host.isEmpty()) {
            host = req.getServerName();
        }
        String scheme = req.isSecure() ? "wss://" : "ws://";
        return scheme + host + req.getRequestURI();
    }

    public int getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    public void setMaxFramePayloadLength(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }
}