package org.example.gamecenter.pojo;

import io.netty.handler.codec.serialization.ObjectEncoder;
import lombok.Data;

@Data
public class WebSocketMessage {
    private String type;
    private String roomId;
    private String timestamp;
    private String messageSource;
    private String messageTarget;
    private Object data;

}
