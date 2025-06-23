//package org.example.gamecenter.statemachine;
//
//import org.example.gamecenter.statemachine.states.GameEvent;
//import org.springframework.stereotype.Component;
//import java.util.EnumMap;
//import java.util.Map;
//
//@Component
//public class GameStateMachine {
//    private GameState currentState;
//    private final Map<GameState, StateHandler> stateHandlers = new EnumMap<>(GameState.class);
//
//    public GameStateMachine() {
//        // 初始化状态处理器
//        stateHandlers.put(GameState.IDLE, new IdleState());
//        stateHandlers.put(GameState.WAITING, new WaitingState());
//        stateHandlers.put(GameState.PLAYING, new PlayingState());
//
//        currentState = GameState.IDLE;
//    }
//
//    public void handleEvent(GameEvent event) {
//        StateHandler handler = stateHandlers.get(currentState);
//        if (handler != null) {
//            handler.handle(this, event);
//        }
//    }
//
//    public void transitionTo(GameState newState) {
//        this.currentState = newState;
//    }
//
//    public GameState getCurrentState() {
//        return currentState;
//    }
//}
