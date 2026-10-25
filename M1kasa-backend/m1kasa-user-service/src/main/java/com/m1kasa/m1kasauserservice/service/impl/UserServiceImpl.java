package com.m1kasa.m1kasauserservice.service.impl;

import com.m1kasa.m1kasauserservice.entity.User;
import com.m1kasa.m1kasauserservice.mapper.UserMapper;
import com.m1kasa.m1kasauserservice.service.UserService;
import com.m1kasa.user.api.UserDTO;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDTO getById(Long id) {
        User user=userMapper.selectById(id);
        if (user==null){
            return null;
        }
        return new UserDTO(user.getId(),user.getNickName(),
                user.getIcon());
    }
}
