package org.example.gamecenter.service.impl;


import lombok.extern.slf4j.Slf4j;
import org.example.gamecenter.client.proxy.ClientProxy;
import org.example.gamecenter.event.KafkaRecordhProducer;
import org.example.gamecenter.pojo.RecordEvent;
import org.example.common.pojo.Result;
import org.example.gamecenter.service.IGameService;
import org.example.gamecenter.websocket.WebSocket;
import org.example.lobbycenter.service.ILobbyRoomService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
@Slf4j
public class GameServiceImpl implements IGameService {
    private static final int BASEPOINT = 1;
    private static final int STATUS_WAITING = 2;
    private static final int STATUS_PLAYING = 3;
    private static final int OFFONLINE = 4;
    @Resource
    private KafkaRecordhProducer kafkaRecordhProducer;

    @Resource
    private KafkaTemplate kafkaTemplate;
    //存储玩家下注结果
    private final ConcurrentHashMap<String, CompletableFuture<Integer>> betResponseMap = new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    //定时任务线程池
    private final ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool(4);
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result exitGame(String userId) {
        String roomId = getRoomId(userId);
        if (roomId == null) return Result.fail("用户不在任何房间中");
        stringRedisTemplate.opsForSet().add("leave_users:" + roomId, userId);
        stringRedisTemplate.opsForZSet().remove("room:" + roomId + ":players", userId);
        stringRedisTemplate.opsForHash().put("players_status:" + userId, "status", String.valueOf(OFFONLINE));
        WebSocket.sendMessageToRoom("用户 " + userId + "离开游戏", roomId);
        log.info("用户 {} 申请退出房间 {}", userId, roomId);
        return Result.ok("已申请退出房间，本回合结束后将退出房间");
    }

    // 真正退出房间
    public Result exitGameAction(String userId) {
        String roomId = getRoomId(userId);
        if (roomId == null) return Result.fail("用户不在任何房间中");
        stringRedisTemplate.opsForZSet().remove("room:" + roomId + ":players", userId);
        stringRedisTemplate.delete("players_status:" + userId);
        stringRedisTemplate.opsForSet().remove("leave_users:" + roomId, userId);
        log.info("用户 {} 成功退出房间 {}", userId, roomId);
        // TODO: 推送消息
        return Result.ok("成功退出房间");
    }

    //todo 解散房间 调用room服务解散房间的接口（gRPC）
    // 检查房间所有成员在线且准备好
    private boolean allPlayersReadyAndOnline(String roomId) {
        Set<String> players = stringRedisTemplate.opsForZSet().range("room:" + roomId + ":players", 0, -1);
        log.info("房间{}所有成员：{}", roomId, players);
        if (players == null || players.isEmpty()) return false;
        for (String userId : players) {
            if (!Boolean.TRUE.equals(WebSocket.isOnline(userId))) {
                WebSocket.sendMessageToRoom("房间" + roomId + "成员" + userId + "掉线，请重新匹配", roomId);
                return false;
            }
            Object status = stringRedisTemplate.opsForHash().get("players_status:" + userId, "status");
            if (!String.valueOf(STATUS_WAITING).equals(String.valueOf(status))) {
                return false;
            }
        }
        return true;
    }


    //    玩家状态 Unready 0 ，Ready 1 ，waiting_begin 2 , Playing 3 ，Offline 3
    @Override
    public Result startGame(String userId) {
        stringRedisTemplate.opsForHash().put("players_status:" + userId, "status", String.valueOf(STATUS_WAITING));
        String roomId = getRoomId(userId);
        if (roomId == null) return Result.fail("用户不在任何房间中");
        if (!allPlayersReadyAndOnline(roomId)) return Result.fail("房间内有成员未准备好或掉线");
        // 所有成员状态置为Playing
        Set<String> players = stringRedisTemplate.opsForZSet().range("room:" + roomId + ":players", 0, -1);
        for (String player : players) {
            stringRedisTemplate.opsForHash().put("players_status:" + player, "status", String.valueOf(STATUS_PLAYING));
        }
        stringRedisTemplate.opsForHash().put("room:" + roomId, "status", "1");
        WebSocket.sendMessageToRoom("游戏开始", roomId);
        asyncExecutor.submit(() -> {
            try {
                gameProcess(roomId);
            } catch (Exception e) {
                log.error("游戏进程异常", e);
            }
        });
        return Result.ok("所有成员已就位，开始游戏");
    }

    private void gameProcess(String roomId) throws IOException, InterruptedException {
        int rounds = 1;
        while (true) {
            List<String> players = getPlayersByScoreAsc(roomId);
            if (players.isEmpty()) {
                endGame(roomId, "房间已无人，游戏结束");
                break;
            }
            // 扣基础分
            for (String userId : players) {
                int score = getUserScore(roomId, userId);
                if (score <= BASEPOINT) {
                    removePlayer(roomId, userId);
                    WebSocket.sendMessageToRoom(userId + " 积分不足被踢出房间", roomId);
                } else {
                    incrUserScore(roomId, userId, -BASEPOINT);
                    incrHotpot(roomId, BASEPOINT);
                }
            }
            if (getHotpot(roomId) <= 0) {
                endGame(roomId, "火锅池积分清空，游戏结束");
                break;
            }
            WebSocket.sendMessageToRoom("开始第" + rounds + "轮游戏", roomId);

            // 随机区间
            Map<String, int[]> abMap = new HashMap<>();
            Random random = new Random();
            for (String userId : players) {
                int a = random.nextInt(10) + 1, b = random.nextInt(10) + 1;
                abMap.put(userId, new int[]{Math.min(a, b), Math.max(a, b)});
                WebSocket.sendMessageTo("你的本轮随机数区间为[" + Math.min(a, b) + ", " + Math.max(a, b) + "]", userId);
            }

            // 下注
            for (String userId : players) {
                int userScore = getUserScore(roomId, userId);
                if (userScore <= 0) {
                    removePlayer(roomId, userId);
                    continue;
                }
                WebSocket.sendMessageToRoom("本轮由" + userId + "下注，请下注(输入0表示放弃，最大可下注" + userScore + "),当前火锅池内积分为：" + getHotpot(roomId) + ",", roomId);
//                WebSocket.sendMessageTo("请下注(输入0表示放弃，最大可下注" + userScore + ")", userId);
                int bet = getUserBet(userId, roomId, userScore);
                if (bet <= 0) {
                    WebSocket.sendMessageToRoom(userId + " 选择放弃本轮", roomId);
                    continue;
                }
                if (bet > userScore) bet = userScore;
                incrUserScore(roomId, userId, -bet);
                incrHotpot(roomId, bet);
                int c = random.nextInt(10) + 1;
                int[] ab = abMap.get(userId);
                boolean win = c >= ab[0] && c <= ab[1];
                if (win) {
                    int winScore = Math.min(getHotpot(roomId), bet * 2);
                    incrUserScore(roomId, userId, winScore);
                    incrHotpot(roomId, -winScore);
                    WebSocket.sendMessageToRoom(userId + " 下注" + bet + "，开出" + c + "，命中[" + ab[0] + "," + ab[1] + "]，赢得" + winScore + "积分！火锅池剩余积分为" + getHotpot(roomId)+"，玩家余额"+getUserScore(roomId, userId), roomId);
                } else {
                    WebSocket.sendMessageToRoom(userId + " 下注" + bet + "，开出" + c + "，未命中[" + ab[0] + "," + ab[1] + "]，未获奖励！火锅池剩余积分为" + getHotpot(roomId), roomId);
                }
                //调用gameReordSendInfo记录对局详情，异步发送不影响主流程
                final String finalRoomId = roomId;
                final int finalRounds = rounds;
                final String finalUserId = userId;
                final int finalWinningBegin = ab[0];
                final int finalWinningEnd = ab[1];
                final int finalBet = bet;
                final int finalWinningRandom = c;
                final int finalRoundResult = win ? 1 : 0;
                final int finalPoolBalance = getHotpot(roomId);
                asyncExecutor.submit(() -> {
                    gameReordSendInfo(finalRoomId, finalRounds, finalUserId, finalWinningBegin,
                            finalWinningEnd, finalBet, finalWinningRandom,
                            finalRoundResult, finalPoolBalance, new Date(System.currentTimeMillis()));
                });

                if (getHotpot(roomId) <= 0) {
                    endGame(roomId, "火锅池积分清空，游戏结束");
                    return;
                }
            }
            // 踢出积分为0的玩家
            for (String userId : new ArrayList<>(players)) {
                if (getUserScore(roomId, userId) <= 0) {
                    WebSocket.sendMessageToRoom(userId + " 积分归零被踢出房间", roomId);
                    removePlayer(roomId, userId);
                }
            }
            if (getPlayersByScoreAsc(roomId).isEmpty()) {
                endGame(roomId, "房间已无人，游戏结束");
                break;
            }
            rounds++;
        }
    }

    private void endGame(String roomId, String message) throws InterruptedException {
        WebSocket.sendMessageToRoom(message, roomId);
        dismissRoom(roomId);
    }

//    private void gameProcess(String roomId) throws IOException {
//        int rounds = 1;
//        while (true) {
//            // 1. 获取玩家列表
//            List<String> playSequence = getPlayersByScoreAsc(roomId);
//            if (playSequence.isEmpty()) {
//                WebSocket.sendMessageToRoom("房间已无人，游戏结束", roomId);
//                break;
//            }
//            // 2. 每人扣除基础积分，放入火锅池
//            for (String userId : playSequence) {
//                int userScore = getUserScore(roomId, userId);
//                if (userScore <= BASEPOINT) {
//                    // 积分不足，踢出
//                    removePlayer(roomId, userId);
//                    WebSocket.sendMessageToRoom(userId + " 积分不足被踢出房间", roomId);
//                } else {
//                    // 扣积分
//                    incrUserScore(roomId, userId, -BASEPOINT);
//                    incrHotpot(roomId, BASEPOINT);
//                }
//            }
//            log.info("火锅池内的积分：" + getHotpot(roomId));
//            // 重新获取玩家列表，踢出后可能有人离开
//            playSequence = getPlayersByScoreAsc(roomId);
//            if (playSequence.isEmpty()) {
//                WebSocket.sendMessageToRoom("房间已无人，游戏结束", roomId);
//                dismissRoom(roomId);
//                break;
//            }
//            log.info("本轮玩家列表：" + playSequence);
//            // 3. 检查火锅池是否为0
//            int hotpot = getHotpot(roomId);
//            if (hotpot <= 0) {
//                WebSocket.sendMessageToRoom("火锅池积分清空，游戏结束", roomId);
//                dismissRoom(roomId);
//                break;
//            }
//            WebSocket.sendMessageToRoom("开始第" + rounds + "轮游戏", roomId);
//            // 4. 生成随机数区间
//            Map<String, int[]> abMap = new HashMap<>();
//            Random random = new Random();
//            for (String userId : playSequence) {
//                int a = random.nextInt(10) + 1;
//                int b = random.nextInt(10) + 1;
//                abMap.put(userId, new int[]{Math.min(a, b), Math.max(a, b)});
//                try {
//                    WebSocket.sendMessageTo("你的本轮随机数区间为[" + Math.min(a, b) + ", " + Math.max(a, b) + "]", userId);
//                } catch (IllegalStateException e) {
//                    log.warn("发送消息给用户 {} 失败: {}", userId, e.getMessage());
//                    removePlayer(roomId, userId);
//                    continue;
//                }
//            }
//
//            // 5. 按顺序让每人选择是否下注
//            for (String userId : playSequence) {
//                int userScore = getUserScore(roomId, userId);
//                if (userScore <= 0) {
//                    log.info("用户 " + userId + " 已被踢出房间");
//                    WebSocket.sendMessageToRoom(userId + "余额不足，已被踢出房间", roomId);
//                    continue; // 已被踢出
//                }
//
//                // 通知玩家操作
//                WebSocket.sendMessageToRoom("本轮由" + userId + "下注，当前火锅池内积分为：" + getHotpot(roomId), roomId);
//                synchronized (userId.intern()) {
//                    try {
//                        WebSocket.sendMessageTo("请下注(输入0表示放弃，最大可下注" + userScore + ")", userId);
//                    } catch (IllegalStateException e) {
//                        log.warn("发送下注请求给用户 {} 失败: {}", userId, e.getMessage());
//                        removePlayer(roomId, userId);
//                        continue;
//                    }
//                }
//                //加入轮询时间，websocket写入，这里读取
//                // 这里需要等待玩家输入下注金额，假设有方法 getUserBet(userId, roomId, maxBet)
//                int bet = getUserBet(userId, roomId, userScore); // 你需要实现等待和获取玩家输入
//                log.info("用户 " + userId + " 下注 " + bet);
//                if (bet <= 0) {
//                    WebSocket.sendMessageToRoom(userId + " 选择放弃本轮", roomId);
//                    continue;
//                }
//                if (bet > userScore) {
//                    log.info("用户 " + userId + " 下注金额大于余额，下注金额改为" + userScore);
//                    WebSocket.sendMessageToRoom("用户" + userId + "梭哈啦，梭哈金额为" + userScore, roomId);
//                    bet = userScore;
//                }
//
//                // 扣除下注积分
//                incrUserScore(roomId, userId, -bet);
//                incrHotpot(roomId, bet);
//
//                // 随机C
//                int c = random.nextInt(10) + 1;
//                int[] ab = abMap.get(userId);
//                boolean win = c >= ab[0] && c <= ab[1];
//
//                if (win) {
//                    int winScore = Math.min(getHotpot(roomId), bet * 2);
//                    incrUserScore(roomId, userId, winScore);
//                    incrHotpot(roomId, -winScore);
//                    WebSocket.sendMessageToRoom(userId + " 下注" + bet + "，开出" + c + "，命中[" + ab[0] + "," + ab[1] + "]，赢得" + winScore + "积分！" + "火锅池剩余积分为" + getHotpot(roomId), roomId);
//                } else {
//                    WebSocket.sendMessageToRoom(userId + " 下注" + bet + "，开出" + c + "，未命中[" + ab[0] + "," + ab[1] + "]，未获奖励！" + "火锅池剩余积分为" + getHotpot(roomId), roomId);
//                }
//
//                // 检查火锅池是否为0
//                if (getHotpot(roomId) <= 0) {
//                    WebSocket.sendMessageToRoom("火锅池积分清空，游戏结束", roomId);
//                    dismissRoom(roomId);
//                    return;
//                }
//            }
//
//            // 6. 检查玩家积分，踢出积分为0的玩家
//            for (String userId : new ArrayList<>(playSequence)) {
//                if (getUserScore(roomId, userId) <= 0) {
//                    removePlayer(roomId, userId);
//                    WebSocket.sendMessageToRoom(userId + " 积分归零被踢出房间", roomId);
//                }
//            }
//
//            // 7. 检查是否还有玩家
//            playSequence = getPlayersByScoreAsc(roomId);
//            if (playSequence.isEmpty()) {
//                WebSocket.sendMessageToRoom("房间已无人，游戏结束", roomId);
//                break;
//            }
//
//            rounds++;
//
//        }
//    }

    //设置定时器，最长时间：30s，超时则判定为不下注，
    private int getUserBet(String userId, String roomId, int userScore) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        betResponseMap.put(userId, future);
        timeoutExecutor.schedule(() -> future.complete(0), 60, TimeUnit.SECONDS);
        try {
            return future.get();
        } catch (Exception e) {
            return 0;
        } finally {
            betResponseMap.remove(userId);
        }
    }

    private String getRoomId(String userId) {
        Object roomObj = stringRedisTemplate.opsForHash().get("players_status:" + userId, "roomId");
        return roomObj == null ? null : roomObj.toString();
    }

    private void dismissRoom(String roomId) throws InterruptedException {
        //todo 完善解散房间逻辑，实际上是调用room服务
        ClientProxy clientProxy = new ClientProxy();
        ILobbyRoomService lobbyRoomService = (ILobbyRoomService) clientProxy.getProxy(ILobbyRoomService.class);
        lobbyRoomService.dismissRoom(roomId);
        WebSocket.sendMessageToRoom("房间已解散", roomId);
    }

    //火锅池内积分用string存储，key为 "hotpot:pool:" + roomId
    private int getHotpot(String roomId) {
        String value = stringRedisTemplate.opsForValue().get("hotpot:pool:" + roomId);
        return value == null ? 0 : Integer.parseInt(value);
    }

    //用户积分用Zset的scoere存储，key为 "room:" + roomId + ":players"，value为userId，score为金币余额
    private void incrUserScore(String roomId, String userId, int score) {
        stringRedisTemplate.opsForZSet().incrementScore("room:" + roomId + ":players", userId, score);
        stringRedisTemplate.opsForZSet().incrementScore("users_coin_count", userId, score);
    }

    private void incrHotpot(String roomId, int score) {
        stringRedisTemplate.opsForValue().increment("hotpot:pool:" + roomId, score);
    }

    private void removePlayer(String roomId, String userId) throws IOException {
        WebSocket.sendMessageTo(userId, "您已被踢出房间");
        stringRedisTemplate.opsForZSet().remove("room:" + roomId + ":players", userId);
        String hashKey = "players_status:" + userId;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(hashKey))) {
            Set<Object> fields = stringRedisTemplate.opsForHash().keys(hashKey);
            if (fields != null && !fields.isEmpty()) {
                stringRedisTemplate.opsForHash().delete(hashKey, fields.toArray(new String[0]));
            }
            // 最后删除整个哈希键
            stringRedisTemplate.delete(hashKey);
        }
    }

    private int getUserScore(String roomId, String userId) {
        Double score = stringRedisTemplate.opsForZSet().score("room:" + roomId + ":players", userId);
        return score == null ? 0 : score.intValue();    }

    public List<String> getPlayersByScoreAsc(String roomId) {
        String key = "room:" + roomId + ":players";
        Set<String> playerSet = stringRedisTemplate.opsForZSet().range(key, 0, -1);
        List<String> result = new ArrayList<>();
        if (playerSet != null) {
            for (Object obj : playerSet) {
                result.add(obj == null ? null : obj.toString());
            }
        }
        return result;
    }

    public void completeUserBet(String userId, int betAmount) {
        CompletableFuture<Integer> future = betResponseMap.get(userId);
        if (future != null) {
            future.complete(betAmount);
        }
    }

    @Override
    public void chatRoom(String userId, String message) {
        String roomId = (String) stringRedisTemplate.opsForHash().get("players_status:" + userId, "roomId");
        WebSocket.sendMessageToRoom(message, roomId);
    }

    public String logInfoOut(String userId) {
        String roomId = (String) stringRedisTemplate.opsForHash().get("players_status:" + userId, "roomId");
        String status = (String) stringRedisTemplate.opsForHash().get("players_status:" + userId, "status");
        String score = String.valueOf(stringRedisTemplate.opsForZSet().score("room:" + roomId + ":players", userId));
        Double usersCoinCount = stringRedisTemplate.opsForZSet().score("users_coin_count", userId);

        return "用户" + userId + "的房间是" + roomId + "，状态是" + status + "，players_status积分是" + score + "，users_coin_count中的积分是" + usersCoinCount;
    }

    private void gameReordSendInfo(String roomId, Integer round, String userId,
                                 Integer winningBegin, Integer winningEnd, Integer betAmount,
                                 Integer winningRandom, Integer roundResult, Integer poolBalance,
                                 Date operateTime) {
        RecordEvent recordEvent = new RecordEvent();

        recordEvent.setTopic("game_record");
        recordEvent.setUserId(userId);

        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("round", round);
        data.put("userId", userId);
        data.put("winningBegin", winningBegin);
        data.put("winningEnd", winningEnd);
        data.put("betAmount", betAmount);
        data.put("winningRandom", winningRandom);
        data.put("roundResult", roundResult);
        data.put("poolBalance", poolBalance);
        data.put("operateTime", operateTime);

        recordEvent.setData(data);
        kafkaRecordhProducer.publishEvent(recordEvent);
    }

}

