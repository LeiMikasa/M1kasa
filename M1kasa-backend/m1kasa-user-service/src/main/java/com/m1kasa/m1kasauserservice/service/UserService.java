package com.m1kasa.m1kasauserservice.service;

import com.m1kasa.user.api.UserDTO;

import java.util.List;

public interface UserService {
    UserDTO getById(Long id);

    List<UserDTO> listByIds(List<Long> ids);
}
