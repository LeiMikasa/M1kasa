package com.m1kasa.m1kasashopservice.service;

import com.m1kasa.m1kasashopservice.entity.Shop;
import com.m1kasa.shop.api.ShopDTO;

import java.util.List;

public interface ShopService {
    ShopDTO getById(Long id);
    boolean update(Shop shop);
    List<ShopDTO> listByType(Long typeId,long current);
    List<ShopDTO> listNearByType(Long typeId,long current,double x,double y);
    Long create(Shop shop);
    List<ShopDTO> searchByName(String name,long current);
}
