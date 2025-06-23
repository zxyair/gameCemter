package org.example.gamecenter.pojo;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 游戏流水记录实体类
 * 对应数据库表 game_flow_record
 */
@Data
@TableName("game_flow_record")
public class GameFlowRecord {
    private Long id;                   // 主键ID
    private String roomId;             // 房间ID
    private Integer round;             // 轮次
    private String userId;             // 玩家ID
    private Integer winningBegin;      // 赢钱开始位置
    private Integer winningEnd;        // 赢钱结束位置
    private Integer betAmount;         // 下注金额
    private Integer winningRandom;     // 赢钱随机数
    private Integer roundResult;       // 回合结果 0:输/1:赢
    private Integer poolBalance;       // 操作后火锅池余额
    private Date operateTime;          // 操作时间
}
