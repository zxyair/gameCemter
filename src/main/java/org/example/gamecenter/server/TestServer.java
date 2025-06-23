package org.example.gamecenter.server;

import org.example.gamecenter.server.provider.ServiceProvider;
import org.example.gamecenter.server.serviceRegister.ServiceRegister;
import org.example.gamecenter.server.serviceRegister.impl.ZKServiceRegister;
import org.example.gamecenter.server.services.RpcServer;
import org.example.gamecenter.server.services.impl.NettyRPCServer;
import org.example.gamecenter.service.IGameService;
import org.example.gamecenter.service.impl.GameServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/*
    @author 张星宇
 */
@Component
public class TestServer {
    @Autowired
    private GameServiceImpl gameService;


    @PostConstruct
    public void initRpcServer() {

        new Thread(() -> {
            IGameService gameService = new GameServiceImpl();
            ServiceProvider serviceProvider = new ServiceProvider("127.0.0.1", 9996);

            serviceProvider.providerServiceProvider(gameService, true);
            RpcServer rpcServer = new NettyRPCServer(serviceProvider);
            rpcServer.start(9996);
        }).start();
    }

}
