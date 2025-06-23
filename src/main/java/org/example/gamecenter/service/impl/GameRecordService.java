package org.example.gamecenter.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.gamecenter.mapper.GameFlowRecordMapper;
import org.example.gamecenter.pojo.GameFlowRecord;
import org.example.gamecenter.service.IGameRecordService;
import org.springframework.stereotype.Service;

@Service
public class GameRecordService extends ServiceImpl<GameFlowRecordMapper,GameFlowRecord> implements IGameRecordService {
}
