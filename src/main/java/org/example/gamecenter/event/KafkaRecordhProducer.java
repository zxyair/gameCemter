package org.example.gamecenter.event;

import com.alibaba.fastjson.JSONObject;
import org.example.gamecenter.pojo.RecordEvent;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class KafkaRecordhProducer {

    @Resource
    private KafkaTemplate kafkaTemplate;

    public void publishEvent(RecordEvent event) {
        // 将事件发布到指定的主题
        kafkaTemplate.send(event.getTopic(), JSONObject.toJSONString(event));
    }

}
