package org.example.gamecenter.websocket;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.example.gamecenter.service.IGameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.activation.DataHandler;
import javax.annotation.Resource;
import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author：JCccc
 * @Description：
 * @Date： created in 15:56 2019/5/13
 */

@Slf4j
@Component
@ServerEndpoint(value = "/game/joinGame/{userId}")
public class WebSocket {
    private static final ConcurrentHashMap<String, Object> userLockMap = new ConcurrentHashMap<>();
    private static IGameService gameService;
    private static RedisTemplate<String, Object> redisTemplate;

    /**
     * 初始化WebSocket依赖项
     * @param gameService 游戏服务
     * @param redisTemplate Redis模板
     */
    public static void initDependencies(IGameService gameService, RedisTemplate<String, Object> redisTemplate) {
        WebSocket.gameService = gameService;
        WebSocket.redisTemplate = redisTemplate;
    }
    /**
     * 在线人数
     */
    public static int onlineNumber = 0;
    /**
     * 以用户的姓名为key，WebSocket为对象保存起来
     */
    private static Map<String, WebSocket> clients = new ConcurrentHashMap<String, WebSocket>();
    /**
     * 会话
     */
    private Session session;
    /**
     * 用户名称
     */
    private String userId;
    private DataHandler SpringApplicationContextUtil;
    /**
     * 建立连接
     *
     * @param session
     */







    @OnOpen
    public void onOpen(@PathParam("userId") String userId, Session session)
    {
        onlineNumber++;
        log.info("现在来连接的客户id："+session.getId()+"用户名："+userId);
        this.userId = userId;
        this.session = session;
        //  logger.info("有新连接加入！ 当前在线人数" + onlineNumber);
        try {
            //messageType 1代表上线 2代表下线 3代表在线名单 4代表普通消息
            //把自己的信息加入到map当中去
            clients.put(userId, this);
            log.info("当前在线人数" + clients.size());
            //给自己发一条消息：成功登陆，在线人数：clients.size()
            Map<String,Object> loginSuccessMsg = new HashMap<>();
            loginSuccessMsg.put("messageType", 1);  // 1表示上线消息
            loginSuccessMsg.put("message", "成功连接游戏服务器");
            loginSuccessMsg.put("userId", userId);
            loginSuccessMsg.put("onlineCount", clients.size());
            sendMessageTo(JSON.toJSONString(loginSuccessMsg), userId);
        }
        catch (IOException e){
            log.info(userId+"上线的时候通知所有人发生了错误");
        }



    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.info("服务端发生了错误"+error.getMessage());
        //error.printStackTrace();
    }
    /**
     * 连接关闭
     */
    @OnClose
    public void onClose()
    {
        checkDependencies();
        onlineNumber--;
        //webSockets.remove(this);
        clients.remove(userId);
        gameService.exitGame(userId);
        userLockMap.remove(userId);
        log.info("用户：" + userId + "已掉线");
    }

    /**
     * 收到客户端的消息
     *
     * @param message 消息
     * @param session 会话
     */
    @OnMessage
    public void onMessage(String message, Session session)
    {
        checkDependencies();
        try{
            Map<String, Object> map = JSON.parseObject(message, Map.class);
            Integer betAmount =  Integer.parseInt(map.get("bet_amount").toString());
            log.info("客户端发送了游戏操作通知，下注金额："+betAmount);
            gameService.completeUserBet(userId,betAmount);

        }catch (Exception e){
            log.info("客户端发送消息时发生了错误"+e.getMessage());
        }

//        try {
//            Map<String, Object> map = JSON.parseObject(message, Map.class);
//            String messageType = (String) map.get("message_type");
//            if (messageType.equals("chat")) {
//                gameService.chatRoom(userId, message);
//            } else if (messageType.equals("game_action_notice")) {
//                log.info("客户端发送了游戏操作通知"+message);
//                Map<String, Object> mapData = JSON.parseObject(map.get("data").toString(), Map.class);
//                int betAmount = mapData.get("betAmount") == null ? 0 : Integer.parseInt(mapData.get("bet_amount").toString());
//                log.info("客户端发送了游戏操作通知，下注金额："+betAmount);
//                gameService.completeUserBet(userId,betAmount);
//            }else{
//                log.info("客户端发送了无效消息"+message);
//            }
//        }
//        catch (Exception e){
//            log.info("客户端发送消息时发生了错误"+e.getMessage());
//        }

    }


    public static void sendMessageTo(String message, String userId) throws IOException {
        WebSocket item = clients.get(userId);
        if (item != null) {
            Object lock = userLockMap.computeIfAbsent(userId, k -> new Object());
            synchronized (lock) {
                item.session.getAsyncRemote().sendText(message);
            }
        }
    }

    public static void sendMessageAll(String message, String fromUserId) throws IOException {
        for (Map.Entry<String, WebSocket> entry : clients.entrySet()) {
            String userId = entry.getKey();
            WebSocket item = entry.getValue();
            Object lock = userLockMap.computeIfAbsent(userId, k -> new Object());
            synchronized (lock) {
                item.session.getAsyncRemote().sendText(message);
            }
        }
    }

    // 发给一个string集合中的所有人
    public static void sendMessageToUsers(String message, Set<String> userIds) throws IOException {
        for (String userId : userIds) {
            WebSocket item = clients.get(userId);
            if (item != null) {
                Object lock = userLockMap.computeIfAbsent(userId, k -> new Object());
                synchronized (lock) {
                    item.session.getAsyncRemote().sendText(message);
                }
            }
        }
    }

    public static void sendMessageToRoom(String message, String roomId) {
        checkDependencies();
        Set<Object> players = redisTemplate.opsForZSet().range("room:" + roomId + ":players", 0, -1);
        if (players != null) {
            for (Object player : players) {
                String userId = player.toString();
                WebSocket item = clients.get(userId);
                if (item != null) {
                    Object lock = userLockMap.computeIfAbsent(userId, k -> new Object());
                    synchronized (lock) {
                        item.session.getAsyncRemote().sendText(message);
                    }
                }
            }
        }
    }
        public static synchronized int getOnlineCount () {
            return onlineNumber;
        }
        public static boolean isOnline (String userId){
            return clients.containsKey(userId);
        }

        private static void checkDependencies () {
            if (gameService == null || redisTemplate == null) {
                throw new IllegalStateException("WebSocket依赖未初始化，请检查配置");
            }
        }

    }



