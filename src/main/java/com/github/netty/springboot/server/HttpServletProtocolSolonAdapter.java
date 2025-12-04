package com.github.netty.springboot.server;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.noear.solon.core.AppClassLoader;
import org.noear.solon.server.prop.impl.HttpServerProps;
import org.noear.solon.core.util.ClassUtil;

import com.github.netty.core.AbstractNettyServer;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.StringUtil;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.servlet.ServletErrorPage;
import com.github.netty.protocol.servlet.SessionCompositeServiceImpl;
import com.github.netty.protocol.servlet.SessionLocalFileServiceImpl;
import com.github.netty.protocol.servlet.SessionLocalMemoryServiceImpl;
import com.github.netty.protocol.servlet.SessionService;
import com.github.netty.protocol.servlet.util.Protocol;
import com.github.netty.springboot.NettyProperties;
import com.github.netty.springboot.SolonUtil.Ssl;
import com.github.netty.springboot.SolonUtil.SslBundle;
import com.github.netty.springboot.SolonUtil.SslBundles;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.ssl.SslContextBuilder;

/**
 * HttpServlet protocol registry (solon adapter)
 *
 * @author wangzihao
 * 2018/11/12/012
 */
public class HttpServletProtocolSolonAdapter extends HttpServletProtocol {
    private static final LoggerX LOGGER = LoggerFactoryX.getLogger(HttpServletProtocolSolonAdapter.class);
    private final NettyProperties properties;
    private Supplier<HttpServerProps> serverPropertiesSupplier;
    private Supplier<Executor> executorSupplier;
    private Supplier<Executor> defaultExecutorSupplier;

    public HttpServletProtocolSolonAdapter(NettyProperties properties, ClassLoader classLoader) {
        super(new com.github.netty.protocol.servlet.ServletContext(classLoader == null ? AppClassLoader.global() : classLoader), null, null);
        this.properties = properties;
    }
    
    public void setExecutorSupplier(Supplier<Executor> executorSupplier) {
        this.executorSupplier = executorSupplier;
        super.setExecutorSupplier(executorSupplier);
    }
    
    public void setDefaultExecutorSupplier(Supplier<Executor> defaultExecutorSupplier) {
        this.defaultExecutorSupplier = defaultExecutorSupplier;
        super.setDefaultExecutorSupplier(defaultExecutorSupplier);
    }


    public void setServerPropertiesSupplier(Supplier<HttpServerProps> serverPropertiesSupplier) {
        this.serverPropertiesSupplier = serverPropertiesSupplier;
    }

    /**
     * WebSocket upgrade handler
     *
     * @param ctx     netty ctx
     * @param request netty request
     */
    @Override
    public void upgradeWebsocket(ChannelHandlerContext ctx, HttpRequest request) {
        // for spring upgradeWebsocket NettyRequestUpgradeStrategy
        ChannelPipeline pipeline = ctx.pipeline();
        addServletPipeline(pipeline, Protocol.http1_1);
        pipeline.fireChannelRegistered();
        pipeline.fireChannelActive();
        pipeline.fireChannelRead(request);
    }

    @Override
    public <T extends AbstractNettyServer> void onServerStart(T server) throws Exception {
        initializerStartup();

        com.github.netty.protocol.servlet.ServletContext servletContext = getServletContext();

        LOGGER.info("Netty servlet on port: {}, with context path '{}'",
                servletContext.getServerAddress().getPort(),
                servletContext.getContextPath()
        );
//        application.scanner("com.github.netty").inject();
    }


    public void configurableServletContext(NettyTcpServerFactory webServerFactory) throws Exception {
        com.github.netty.protocol.servlet.ServletContext servletContext = getServletContext();
        HttpServerProps serverProperties = serverPropertiesSupplier != null ? serverPropertiesSupplier.get() : null;
        NettyProperties.HttpServlet httpServlet = properties.getHttpServlet();

        InetSocketAddress address = NettyTcpServerFactory.getServerSocketAddress(webServerFactory.getAddress(), webServerFactory.getPort());
        //Server port
        servletContext.setServerAddress(address);
        servletContext.setInstanceFactory(new ApplicationInstanceFactory(servletContext.getClassLoader(), properties.getApplication()));
        servletContext.setEnableUrlServletAntPathMatcher(httpServlet.isEnableUrlServletAntPathMatcher());
        servletContext.setEnableUrlFilterAntPathMatcher(httpServlet.isEnableUrlFilterAntPathMatcher());
        servletContext.setMapperContextRootRedirectEnabled(httpServlet.isMapperContextRootRedirectEnabled());
        servletContext.setUseRelativeRedirects(httpServlet.isUseRelativeRedirects());
        servletContext.setEnableLookupFlag(httpServlet.isEnableNsLookup());
        servletContext.setAutoFlush(httpServlet.getAutoFlushIdleMs() > 0);
        servletContext.setUploadFileTimeoutMs(httpServlet.getUploadFileTimeoutMs());
        servletContext.setAbortAfterMessageTimeoutMs(httpServlet.getAbortAfterMessageTimeoutMs());
        servletContext.setContextPath(webServerFactory.getContextPath());
        servletContext.setServerHeader(webServerFactory.getServerHeader());
        servletContext.setServletContextName(webServerFactory.getDisplayName());
        servletContext.getErrorPageManager().setShowErrorMessage(httpServlet.isShowExceptionMessage());
        //Session timeout
        servletContext.setSessionTimeout((int) webServerFactory.getSession().getTimeout().getSeconds());
        servletContext.setSessionService(newSessionService(properties, servletContext));
        for (MimeMappings.Mapping mapping : webServerFactory.getMimeMappings()) {
            servletContext.getMimeMappings().add(mapping.getExtension(), mapping.getMimeType());
        }
        servletContext.getNotExistBodyParameters().addAll(Arrays.asList(httpServlet.getNotExistBodyParameter()));

        NettyProperties.HttpServlet.ServerCompression compression = properties.getHttpServlet().getCompression();
        if (compression != null && compression.isEnabled()) {
            super.setEnableContentCompression(compression.isEnabled());
            super.setContentSizeThreshold((int)compression.getMinResponseSize());
            super.setCompressionMimeTypes(compression.getMimeTypes().clone());
        }
        if (serverProperties != null) {
            super.setMaxHeaderSize((int)serverProperties.getMaxHttpRequestHeaderSize());
        }
        Boolean enableH2 = httpServlet.getEnableH2();
        if (enableH2 == null) {
            enableH2 = webServerFactory.getHttp2().isEnabled();
        }
        // https2
        super.setEnableH2(enableH2);
        // http2
        super.setEnableH2c(httpServlet.isEnableH2c());
        // ws
        super.setEnableWebsocket(httpServlet.isEnableWebsocket());
        // https, wss
        NettyProperties.Ssl ssl = properties.getSsl();
        if (ssl != null && ssl.isEnabled()) {
            SslContextBuilder sslContextBuilder = SolonUtil.newSslContext(ssl);
            super.setSslContextBuilder(sslContextBuilder);
        }

        String location = null;
        NettyProperties.HttpServlet.MultipartConfig multipartConfig = properties.getHttpServlet().getMultipart();
        if (multipartConfig != null && multipartConfig.isEnabled()) {
            super.setMaxChunkSize(multipartConfig.getMaxRequestSize());
            servletContext.setFileSizeThreshold(multipartConfig.getFileSizeThreshold());
            location = multipartConfig.getLocation();
        }

        if (location != null && !location.isEmpty()) {
            servletContext.setDocBase(location, "");
        } else {
            servletContext.setDocBase(webServerFactory.getDocumentRoot().getAbsolutePath());
        }

        //Error page
        List<NettyProperties.ErrorPage> errorPages = properties.getHttpServlet().getErrorPages();
        if (errorPages != null) {
            for (NettyProperties.ErrorPage errorPage : errorPages) {
                ServletErrorPage servletErrorPage = new ServletErrorPage(errorPage.getStatusCode(), errorPage.getException(), errorPage.getPath());
                servletContext.getErrorPageManager().add(servletErrorPage);
            }
        }

        // cookieSameSite
        if (properties.getHttpServlet().getCookieSameSite() != null) {
            final String sameSiteValue = properties.getHttpServlet().getCookieSameSite();
            servletContext.setCookieSameSiteSupplier((cookie, httpServletRequest) -> sameSiteValue);
        }
    }

    /**
     * New session service
     *
     * @param properties     properties
     * @param servletContext servletContext
     * @return SessionService
     */
    protected SessionService newSessionService(NettyProperties properties, com.github.netty.protocol.servlet.ServletContext servletContext) {
        //Composite session (default local storage)
        SessionService sessionService;
        NettyProperties.HttpServlet httpServlet = properties.getHttpServlet();
        if (StringUtil.isNotEmpty(httpServlet.getSessionRemoteServerAddress())) {
            //Enable session remote storage using RPC
            String remoteSessionServerAddress = httpServlet.getSessionRemoteServerAddress();
            InetSocketAddress address;
            if (remoteSessionServerAddress.contains(":")) {
                String[] addressArr = remoteSessionServerAddress.split(":");
                address = new InetSocketAddress(addressArr[0], Integer.parseInt(addressArr[1]));
            } else {
                address = new InetSocketAddress(remoteSessionServerAddress, 80);
            }
            SessionCompositeServiceImpl compositeSessionService = new SessionCompositeServiceImpl(servletContext);
            compositeSessionService.enableRemoteRpcSession(address,
                    80,
                    1,
                    properties.getNrpc().isClientEnableHeartLog(),
                    properties.getNrpc().getClientHeartIntervalTimeMs(),
                    properties.getNrpc().getClientReconnectScheduledIntervalMs());
            sessionService = compositeSessionService;
        } else if (httpServlet.isEnablesLocalFileSession()) {
            //Enable session file storage
            sessionService = new SessionLocalFileServiceImpl(servletContext.getResourceManager(), servletContext);
        } else {
            sessionService = new SessionLocalMemoryServiceImpl(servletContext);
        }
        return sessionService;
    }

}