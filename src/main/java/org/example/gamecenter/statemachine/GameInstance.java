//package org.example.gamecenter.statemachine;
//
//import org.example.gamecenter.statemachine.states.GameStates;
//
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//
//public class GameInstance {
//    private GameStates state = GameStates.WAITING;
//    private List<String> players = new ArrayList<>();
//    private Set<String> readyPlayers = new HashSet<>();
//    // 其他游戏数据...
//
//    // 玩家准备
//    public synchronized void playerReady(String playerId) {
//        if (state != GameStates.WAITING) return;
//        readyPlayers.add(playerId);
//        if (readyPlayers.size() == players.size()) {
//            changeState(GameStates.DEALING);
//        }
//    }
//
//    // 状态切换
//    private void changeState(GameStates newState) {
//        this.state = newState;
//        switch (newState) {
//            case DEALING:
//                dealCards();
//                break;
//            case PLAYING:
//                startPlaying();
//                break;
//            case SETTLEMENT:
//                settle();
//                break;
//            case END:
//                destroy();
//                break;
//            default:
//                break;
//        }
//    }
//
//    // 发牌
//    private void dealCards() {
//        // 发牌逻辑...
//        changeState(GameStates.PLAYING);
//    }
//
//    // 游戏进行
//    private void startPlaying() {
//        // 游戏主逻辑...
//    }
//
//    // 玩家操作
//    public synchronized void playerAction(String playerId, Object action) {
//        if (state != GameStates.PLAYING) return;
//        // 处理玩家操作...
//        // 判断是否游戏结束
//        if (isGameOver()) {
//            changeState(GameStates.SETTLEMENT);
//        }
//    }
//
//    // 结算
//    private void settle() {
//        // 结算逻辑...
//        changeState(GameStates.END);
//    }
//
//    // 房间销毁
//    private void destroy() {
//        // 清理资源...
//    }
//
//    // 判断游戏是否结束
//    private boolean isGameOver() {
//        // 判断逻辑...
//        return false;
//    }
//}