package com.github.netty.protocol.servlet.websocket;

interface Constants {
	
	// HTTP upgrade header names and values
    String HOST_HEADER_NAME = "Host";
    String UPGRADE_HEADER_NAME = "Upgrade";
    String UPGRADE_HEADER_VALUE = "websocket";
    String ORIGIN_HEADER_NAME = "Origin";
    String CONNECTION_HEADER_NAME = "Connection";
    String CONNECTION_HEADER_VALUE = "upgrade";
    String LOCATION_HEADER_NAME = "Location";
    String AUTHORIZATION_HEADER_NAME = "Authorization";
    String WWW_AUTHENTICATE_HEADER_NAME = "WWW-Authenticate";
    String PROXY_AUTHORIZATION_HEADER_NAME = "Proxy-Authorization";
    String PROXY_AUTHENTICATE_HEADER_NAME = "Proxy-Authenticate";
    String WS_VERSION_HEADER_NAME = "Sec-WebSocket-Version";
    String WS_VERSION_HEADER_VALUE = "13";
    String WS_KEY_HEADER_NAME = "Sec-WebSocket-Key";
    String WS_PROTOCOL_HEADER_NAME = "Sec-WebSocket-Protocol";
    String WS_EXTENSIONS_HEADER_NAME = "Sec-WebSocket-Extensions";

}
