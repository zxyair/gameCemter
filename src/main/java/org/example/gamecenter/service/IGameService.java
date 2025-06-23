package org.example.gamecenter.service;

import org.example.common.pojo.Result;


public interface IGameService  {
    Result exitGame(String userId);

    Result startGame(String userId);

    void completeUserBet(String userId, int betAmount);

    void chatRoom(String userId, String message);
}
