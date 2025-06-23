package org.example.gamecenter.pojo;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class ActionNoticeReq {
    private int round;
    private String actionUserId;
    private int operationSequence;
    private int winning_begin;
    private int winning_end;
    private int poolBalance;
    private int maxBetAmount;

}
