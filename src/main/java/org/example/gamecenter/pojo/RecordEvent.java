package org.example.gamecenter.pojo;


import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class RecordEvent {

    private String topic;
    private String userId;
//    private Long entityId;
    private Map<String, Object> data = new HashMap<>();

}