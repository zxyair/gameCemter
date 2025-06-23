package org.example.gamecenter.server.services;

/*
    @author 张星宇
 */
public interface RpcServer {
    void start(int port);
    void stop();
}
