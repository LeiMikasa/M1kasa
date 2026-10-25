package com.m1kasa.m1kasauserservice.service;

public interface LoginService {
    String login(String phone,String code);
    void logout(String token);
}
