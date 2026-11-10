package com.m1kasa.m1kasasearchservice.service;

import com.m1kasa.shop.api.ShopDTO;
import com.m1kasa.shop.api.message.ShopSearchSyncMessage;

import java.io.IOException;
import java.util.List;

public interface ShopIndexService {
    /*
    * 将一批店铺写入ES
    *
    * */
    int saveBatch(List<ShopDTO> shops) throws IOException;

    // 同步一条店铺变化消息
    void sync(ShopSearchSyncMessage message) throws IOException;
}
