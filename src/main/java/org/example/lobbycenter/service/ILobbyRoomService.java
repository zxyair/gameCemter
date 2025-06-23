package org.example.lobbycenter.service;

import org.example.common.pojo.Result;
import org.springframework.stereotype.Service;

@Service
public interface ILobbyRoomService {
    Result createRoom(String ownerId, Integer maxPlayers);

    Result joinRoom(String userId, String roomId);

    Result startGame(String roomId);

    Result leaveRoom(String userId, String roomId);

    Result dismissRoom(String roomId);

    Result listRoom(Integer page);
}
