package org.example.gamecenter.event;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.example.gamecenter.pojo.GameFlowRecord;
import org.example.gamecenter.pojo.RecordEvent;
import org.example.gamecenter.service.IGameRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.*;

import static org.example.gamecenter.utils.KafkaConstants.FLOW_RECORD_TOPIC;


//todo 可以采用线程池的方式进行处理，可以批量拉取
//todo kafka可以集群部署，并且配置多个消费者
//todo 还需要考虑死信队列、容灾、持久化这些东西

@Component
@Slf4j
public class KafkaRecordConsumer {

    @Qualifier("gameRecordService")
    @Autowired
    private IGameRecordService gameFlowRecordService;

    @KafkaListener(topics = {FLOW_RECORD_TOPIC}, containerFactory = "kafkaListenerContainerFactory")
    public void handleCreateOrder(String message) {
        log.info("收到消息: {}", message);
       try{
           RecordEvent event = JSONObject.parseObject(message, RecordEvent.class);            // 从事件数据中提取信息
            Map<String, Object> data = event.getData();

            // 创建数据库记录对象
            GameFlowRecord record = new GameFlowRecord();
            record.setRoomId((String) data.get("roomId"));
            record.setRound((Integer) data.get("round"));
            record.setUserId((String) data.get("userId"));
            record.setWinningBegin((Integer) data.get("winningBegin"));
            record.setWinningEnd((Integer) data.get("winningEnd"));
            record.setBetAmount((Integer) data.get("betAmount"));
            record.setWinningRandom((Integer) data.get("winningRandom"));
            record.setPoolBalance((Integer) data.get("poolBalance"));
            Long operateTimeStamp = ((Number) data.get("operateTime")).longValue();
            record.setOperateTime(new Date(operateTimeStamp));

            // 设置结果类型(1:赢 0:输)
            record.setRoundResult((Integer) data.get("roundResult"));

            // 保存到数据库
            gameFlowRecordService.save(record);

            log.info("成功保存游戏记录: 房间{} 第{}轮 用户{} 下注{} 结果{}",
                    record.getRoomId(),
                    record.getRound(),
                    record.getUserId(),
                    record.getBetAmount(),
                    record.getRoundResult() == 1 ? "赢" : "输");
        }
       catch (Exception e){
            log.error("保存游戏记录失败:{}",e.getMessage());
            e.printStackTrace();
        }

    }

}

