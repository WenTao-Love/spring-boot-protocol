package io.netty.channel.epoll;

import io.netty.channel.Channel;

public class EpollUtils {
	
	private EpollUtils() {}

    public static boolean forceFlush(Channel.Unsafe unsafe) {
        if (unsafe instanceof AbstractEpollChannel.AbstractEpollUnsafe) {
            AbstractEpollChannel.AbstractEpollUnsafe epollUnsafe = (AbstractEpollChannel.AbstractEpollUnsafe) unsafe;
            epollUnsafe.epollOutReady();
            return true;
        } else {
            return false;
        }
    }
}
