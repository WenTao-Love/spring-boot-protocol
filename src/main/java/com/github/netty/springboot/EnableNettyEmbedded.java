package com.github.netty.springboot;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Import;

import com.github.netty.core.AbstractProtocol;
import com.github.netty.springboot.server.HttpServletProtocolSolonAdapter;
import com.github.netty.springboot.server.NRpcProtocolSolonAdapter;
import com.github.netty.springboot.server.NettyEmbeddedAutoConfiguration;
import com.github.netty.springboot.server.NettyRequestUpgradeStrategy;
import com.github.netty.springboot.server.NettyTcpServerFactory;

/**
 * Enable embedded TCP container.
 * It will enable.
 * 1. http server protocol,
 * Servlet Web or Reactive Web. {@link NettyTcpServerFactory} {@link HttpServletProtocolSolonAdapter}
 * Websocket. {@link NettyRequestUpgradeStrategy}
 * 2. rpc server protocol. {@link NRpcProtocolSolonAdapter}
 * 3. and user-defined protocols..
 * 
 * If you want to add your own protocol,  you only need implement {@link AbstractProtocol}, Next restart, do not need to do other things
 * <pre> {@code
 *     \@Component
 *     public class MyProtocolsRegister extends AbstractProtocolsRegister{
 *          public static final byte[] PROTOCOL_HEADER = {
 *                  'M', 'Y',
 *                  'H', 'E', 'A', 'D', 'E', 'R'
 *          };
 *
 *          public String getProtocolName() {
 *              return "my-protocol";
 *          }
 *
 *          public boolean canSupport(ByteBuf msg) {
 *              if (msg.readableBytes() < PROTOCOL_HEADER.length) {
 *                  return false;
 *              }
 *              for (int i = 0; i < PROTOCOL_HEADER.length; i++) {
 *                  if (msg.getByte(msg.readerIndex() + i) != PROTOCOL_HEADER[i]) {
 *                      return false;
 *                  }
 *              }
 *              return true;
 *          }
 *
 *          public void addPipeline(Channel channel, ByteBuf clientFirstMsg) throws Exception {
 *              channel.pipeline().addLast(new StringDecoder());
 *              channel.pipeline().addLast(new StringEncoder());
 *              channel.pipeline().addLast(new MyChannelHandler());
 *          }
 *     }
 *
 * }</pre>
 * 
 * -----------------------------------------------------------
 * If you want to enable websocket protocol,  you need use NettyRequestUpgradeStrategy.class.
 * example..
 * <pre> {@code
 * public class WebsocketConfig extends AbstractWebSocketMessageBrokerConfigurer {
 *     public RequestUpgradeStrategy requestUpgradeStrategy() {
 *         // return new JettyRequestUpgradeStrategy();
 *         // return new TomcatRequestUpgradeStrategy();
 *         return new NettyRequestUpgradeStrategy();
 *     }
 *
 *     public void registerStompEndpoints(StompEndpointRegistry registry) {
 *         StompWebSocketEndpointRegistration endpoint = registry.addEndpoint("/my-websocket");
 *         endpoint.setHandshakeHandler(new DefaultHandshakeHandler(requestUpgradeStrategy()) {
 *             protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
 *                 String token = request.getHeaders().getFirst("access_token");
 *                 return () -> token;
 *             }
 *         });
 *         endpoint.setAllowedOrigins("*").withSockJS();
 *     }
 *
 *     public void configureMessageBroker(MessageBrokerRegistry registry) {
 *         registry.enableSimpleBroker("/topic/");
 *         registry.setApplicationDestinationPrefixes("/app");
 *         registry.setUserDestinationPrefix("/user/");
 *     }
 *  }
 * }</pre>
 *
 * @author wangzihao 2019-11-2 00:58:11
 * @see com.github.netty.springboot.NettyProperties
 * @see com.github.netty.springboot.server.NettyEmbeddedAutoConfiguration
 * @see com.github.netty.springboot.server.NettyTcpServerFactory
 * @see com.github.netty.springboot.server.HttpServletProtocolSolonAdapter
 * @see NRpcProtocolSolonAdapter
 * @see com.github.netty.springboot.server.NettyRequestUpgradeStrategy
 * @see com.github.netty.core.AbstractProtocol
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
@Condition(onBean = NettyProperties.class)
@Import({NettyEmbeddedAutoConfiguration.class})
public @interface EnableNettyEmbedded {

}
