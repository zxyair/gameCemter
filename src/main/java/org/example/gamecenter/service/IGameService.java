package org.example.gamecenter.service;

import org.example.gamecenter.pojo.Result;
import org.springframework.stereotype.Service;


public interface IGameService  {
    Result exitGame(String userId);

    Result startGame(String userId);

    void completeUserBet(String userId, int betAmount);

    void chatRoom(String userId, String message);
}
