package com.m1kasa.m1kasauserservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasauserservice.entity.User;
import com.m1kasa.m1kasauserservice.mapper.UserMapper;
import com.m1kasa.m1kasauserservice.service.UserService;
import com.m1kasa.user.api.UserDTO;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Override
    public List<UserDTO> listByIds(List<Long> ids) {
        //没有需要查询的用户，直接返回空列表
        if (ids==null || ids.isEmpty()){
            return List.of();
        }
        //限制一次查询的数量
        if (ids.size()>100){
            throw new IllegalArgumentException("一次最多查询 100 个用户");
        }
        //所有用户ID必须是正数
        boolean invalid = ids.stream().anyMatch(id -> id == null || id <= 0);

        if (invalid){
            throw new IllegalArgumentException("用户ID必须大于0");
        }
        //多篇笔记可能属于同一个作者，先去重
        List<Long> distinctIds = ids.stream().distinct().toList();

        //一次查询，只取对外展示需要的字段
        List<User> users = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .select(
                                User::getId,
                                User::getNickName,
                                User::getIcon
                        )
                        .in(User::getId, distinctIds)
        );

        //将数据库实体转换成对外返回的DTO
        return users.stream()
                .map(user -> new UserDTO(
                        user.getId(),
                        user.getNickName(),
                        user.getIcon()
                )).toList();
    }
}
