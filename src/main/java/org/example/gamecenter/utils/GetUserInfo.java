package org.example.gamecenter.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

import static org.example.gamecenter.utils.RedisConstants.LOGIN_USER_KEY;
@Component
public class GetUserInfo {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public  String getUserIdByToken(String token) {

        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
        if (userMap.isEmpty()) {
            return null;
        }
        System.out.println("token:" + token);
        System.out.println("userId:" + userMap.get("id").toString());
        return userMap.get("id").toString();
    }
}
