package com.github.netty.protocol.servlet;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.SessionCookieConfig;

/**
 * Configuration of session cookies
 *
 * @author wangzihao
 * 2018/7/14/014
 */
public class ServletSessionCookieConfig implements SessionCookieConfig {
    boolean httpOnly;
    boolean secure;
    /**
     * Unit seconds
     */
    private int maxAge = -1;
    private String comment;
    String domain;
    private String name;
    private String path;
    
    private final Map<String,String> attributes = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public ServletSessionCookieConfig() {
    }


    @Override
    public String getComment() {
        return comment;
    }

    @Override
    public void setComment(String comment) {
        this.comment = comment;
    }

    @Override
    public String getDomain() {
        return domain;
    }

    @Override
    public void setDomain(String domain) {
        this.domain = domain;
    }

    @Override
    public int getMaxAge() {
        return maxAge;
    }

    @Override
    public void setMaxAge(int maxAge) {
        this.maxAge = maxAge;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public boolean isHttpOnly() {
        return httpOnly;
    }

    @Override
    public void setHttpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
    }

    @Override
    public boolean isSecure() {
        return secure;
    }

    @Override
    public void setSecure(boolean secure) {
        this.secure = secure;
    }


	@Override
	public void setAttribute(String name, String value) {
		attributes.put(name, value);
	}


	@Override
	public String getAttribute(String name) {
		return attributes.get(name);
	}


	@Override
	public Map<String, String> getAttributes() {
		return Collections.unmodifiableMap(attributes);
	}

}
