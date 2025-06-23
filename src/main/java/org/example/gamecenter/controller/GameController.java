package org.example.gamecenter.controller;

import org.example.common.pojo.Result;
import org.example.gamecenter.utils.GetUserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.example.gamecenter.service.IGameService;
@RestController
@RequestMapping("/game")
public class GameController {
    @Autowired
    private GetUserInfo getUserInfo;

    @Autowired
    private  IGameService gameService;

    @PostMapping("/exit")
    public Result exitGame(
            @RequestHeader("Authorization") String token) {
        // 从Redis中根据token获取用户ID
        String userId = getUserInfo.getUserIdByToken(token);
        if (userId == null || userId.isEmpty()) {
            return Result.fail("无效的token或用户未登录");
        }
        return gameService.exitGame(userId);
    }

    @PostMapping("/start")
    public Result startGame(
            @RequestHeader("Authorization") String token) {
        // 从Redis中根据token获取用户ID
        String userId = getUserInfo.getUserIdByToken(token);
        if (userId == null || userId.isEmpty()) {
            return Result.fail("无效的token或用户未登录");
        }
        return gameService.startGame(userId);
    }
}
