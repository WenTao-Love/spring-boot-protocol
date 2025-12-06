package com.github.netty.core.util;

import io.netty.util.concurrent.DefaultThreadFactory;

/**
 * Created by wangzihao on 2018/8/25/025.
 */
public class ThreadFactoryX extends DefaultThreadFactory implements java.util.concurrent.ThreadFactory {

    private final String preName;
    private final boolean daemon;
    private final ThreadGroup threadGroup;

    public ThreadFactoryX(String preName, Class<?> poolType) {
        this(preName, poolType, Thread.MAX_PRIORITY, false);
    }

    public ThreadFactoryX(String preName, Class<?> poolType, int priority, boolean daemon) {
        super(NamespaceUtil.newIdName(poolType), priority);
        this.preName = preName;
        this.daemon = daemon;
        this.threadGroup = Thread.currentThread().getThreadGroup();
    }

    public ThreadFactoryX(String poolName, String preName, boolean daemon) {
        super(poolName);
        this.preName = preName;
        this.threadGroup = Thread.currentThread().getThreadGroup();
        this.daemon = daemon;
    }

    @Override
    protected Thread newThread(Runnable r, String name) {
        Thread thread = new NettyThreadX(threadGroup, r, name);
        if (preName != null && !preName.isEmpty()) {
            thread.setName("NettyX-" + preName + "-" + thread.getName());
        }
        if (daemon) {
            thread.setDaemon(true);
        }
        return thread;
    }
}
