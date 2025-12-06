package com.github.netty.solon.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;

import org.noear.solon.server.ServerConstants;
import org.noear.solon.server.ServerLifecycle;
import org.noear.solon.server.http.HttpServerConfigure;
import org.noear.solon.server.prop.impl.HttpServerProps;
import org.noear.solon.server.ssl.SslConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.netty.StartupServer;
import com.github.netty.protocol.HttpServletProtocol;
import com.github.netty.protocol.servlet.ServletContext;

public abstract class NettyTcpServerBase  implements ServerLifecycle, HttpServerConfigure{
	static final Logger log = LoggerFactory.getLogger(NettyTcpServerBase.class);
	
	protected StartupServer _server;
	protected final HttpServerProps props;
    protected SslConfig sslConfig = new SslConfig(ServerConstants.SIGNAL_HTTP);

    protected Set<Integer> addHttpPorts = new LinkedHashSet<>();

    protected boolean enableHttp2 = false;
    protected boolean enableWebSocket;
    
    public NettyTcpServerBase(HttpServerProps props) {
    	this.props = props;
    }

	@Override
	public void enableSsl(boolean enable, SSLContext sslContext) {
		sslConfig.set(enable, sslContext);
	}

	@Override
	public void addHttpPort(int port) {
		addHttpPorts.add(port);
	}

	@Override
	public void setExecutor(Executor executor) {
		log.warn("Netty Server Servlet does not support user-defined executor");
	}
	
	public HttpServerProps getProps() {
        return props;
    }

	@Override
	public void start(String host, int port) throws Throwable {
		_server = new StartupServer(new InetSocketAddress(host,port));
		ServletContext _serverContext = initServletContext();
		
		_server.addProtocol(new HttpServletProtocol(_serverContext));
		_server.start();
	}

	@Override
	public void stop() throws Throwable {
		if(_server != null) {
			_server.stop();
			_server = null;
		}
		
	}
	
	protected abstract ServletContext initServletContext() throws IOException;
	

}
