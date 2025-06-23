package org.example.gamecenter.client.netty.nettyInitializer;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import org.example.gamecenter.client.netty.handler.NettyClientHandler;
import org.example.common.serializer.impl.ObjectSerializer;
import org.example.common.serializer.myCode.MyDecode;
import org.example.common.serializer.myCode.MyEncode;


/*
    @author 张星宇
 */
public class NettyClientInitializer extends ChannelInitializer<SocketChannel> {
    // netty自带编码解码器，JDK序列化，通过handler添加长度字段解决粘包半包问题
/*    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(
                new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,0,
                        4,0,4));
        pipeline.addLast(new LengthFieldPrepender(4));
        pipeline.addLast(new ObjectEncoder());
        pipeline.addLast(new ObjectDecoder(new ClassResolver() {
            @Override
            public Class<?> resolve(String className) throws ClassNotFoundException {
                return Class.forName(className);
            }
        }));
        pipeline.addLast(new NettyClientHandler());
    }*/

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        System.out.println("client initChannel");
        // 客户端应该先编码请求，再解码响应
        pipeline.addLast(new MyEncode(new ObjectSerializer())); // 编码请求
        pipeline.addLast(new MyDecode()); // 解码响应
        pipeline.addLast(new NettyClientHandler()); // 业务处理
        System.out.println("客户端处理器链初始化完成");
    }
}
