package org.example.gamecenter.client.serviceCenter;

import java.net.InetSocketAddress;

/*
    @author 张星宇
 */
public interface ServiceCenter {
    InetSocketAddress serviceDiscovery(String serviceName);
    boolean checkRetry(String serviceName);
}
