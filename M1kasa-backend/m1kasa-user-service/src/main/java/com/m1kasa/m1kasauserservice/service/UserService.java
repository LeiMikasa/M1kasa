package com.m1kasa.m1kasauserservice.service;

import com.m1kasa.user.api.UserDTO;

public interface UserService {
    UserDTO getById(Long id);
}
