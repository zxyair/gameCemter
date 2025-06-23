package org.example.gamecenter.client.rpcClient.impl;


import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.example.gamecenter.client.netty.nettyInitializer.NettyClientInitializer;
import org.example.gamecenter.client.rpcClient.RpcClient;
import org.example.gamecenter.client.serviceCenter.ServiceCenter;
import org.example.gamecenter.client.serviceCenter.ZKServiceCenter;
import org.example.common.Message.RpcRequest;
import org.example.common.Message.RpcResponse;


import java.net.InetSocketAddress;


/*
    @author 张星宇
 */
public class NettyRpcClient implements RpcClient {
    private static  final Bootstrap bootstrap = new Bootstrap();
    private static final EventLoopGroup group = new NioEventLoopGroup();
    private ServiceCenter serviceCenrer;

    public NettyRpcClient(ServiceCenter serviceCenter) throws InterruptedException {
        this.serviceCenrer = new ZKServiceCenter();
    }

    static {
        bootstrap.group(group).channel(NioSocketChannel.class).handler(new NettyClientInitializer());
    }

    public RpcResponse sendRequest(RpcRequest request) {

        InetSocketAddress address = serviceCenrer.serviceDiscovery(request.getInterfaceName());
        //
        String host = address.getHostName();
        System.out.println("获取到地址"+host);
        int port = address.getPort();
        System.out.println("获取到端口"+port);

        try {
            ChannelFuture future = bootstrap.connect(host, port).sync();
            Channel channel = future.channel();
            ChannelFuture sendFuture = channel.writeAndFlush(request);
            sendFuture.addListener(result -> {
                if (result.isSuccess()) {
                    System.out.println("请求已成功写入TCP发送缓冲区");
                } else {
                    System.err.println("请求发送失败: " + result.cause());
                }
            });
            System.out.println("尝试发送请求: " + request);
            // 等待关闭
            channel.closeFuture().sync();
            AttributeKey<RpcResponse> key = AttributeKey.valueOf("RPCResponse");
            RpcResponse response = channel.attr(key).get();
            System.out.println(response);
            return  response;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return  null;
        }
    }
    private String simplifyServiceName(String fullServiceName) {
        // 从全类名中提取最后的接口名，如 org.example.lobbycenter.service.ILobbyRoomService -> ILobbyRoomService
        return fullServiceName.substring(fullServiceName.lastIndexOf('.') + 1);
    }
}


