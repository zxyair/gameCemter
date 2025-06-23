package org.example.gamecenter.pojo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class ActionNoticeResp {
    private int round;
    private String actionUserId;
    private int operationSequence;
    private int actionType;
    private int betAmount;
}
