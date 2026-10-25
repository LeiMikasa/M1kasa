package com.m1kasa.m1kasauserservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasauserservice.entity.User;
import com.m1kasa.m1kasauserservice.mapper.UserMapper;
import com.m1kasa.m1kasauserservice.service.LoginService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
@Service
public class LoginServiceImpl implements LoginService {
    private static final DefaultRedisScript<Long> VERIFY_CODE=
            new DefaultRedisScript<>(
                    """
                            local stored = redis.call('GET', KEYS[1])
                            if stored == ARGV[1] then
                                return redis.call('DEL', KEYS[1])
                            end
                            return 0
                     
                            """,Long.class
            );
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public LoginServiceImpl(UserMapper userMapper, StringRedisTemplate stringRedisTemplate) {
        this.userMapper = userMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String login(String phone, String code) {
          Long verified=stringRedisTemplate.execute(
                  VERIFY_CODE,
                  List.of("m1kasa:login:code:"+phone),
                  code
          );
          if (!Long.valueOf(1).equals(verified)){
              return null;
          }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getPhone, phone)
        );
        if (user==null){
            user=new User();
            user.setPhone(phone);
            user.setPassword("");
            user.setNickName("user_"+
                    UUID.randomUUID().toString().substring(0,8));
            user.setIcon("");
            LocalDateTime now=LocalDateTime.now();
            user.setCreateTime(now);
            user.setUpdateTime(now);

            if (userMapper.insert(user)!=1){
                throw new IllegalStateException("用户创建失败");
            }
        }
        String token=UUID.randomUUID().toString().replace("-","");
        stringRedisTemplate.opsForValue().set(
                "m1kasa:login:token:" + token,
                user.getId().toString(),
                Duration.ofMinutes(30)
        );
        return token;
    }

    @Override
    public void logout(String token) {
        stringRedisTemplate.delete("m1kasa:login:token:"+token);
    }
}
