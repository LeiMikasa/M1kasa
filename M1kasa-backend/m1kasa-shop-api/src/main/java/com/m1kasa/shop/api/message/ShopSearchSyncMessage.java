package com.m1kasa.shop.api.message;

import com.m1kasa.shop.api.ShopDTO;

public record ShopSearchSyncMessage(
        String requestId,   // 本次修改的消息 ID
        Long shopId,        // 店铺 ID
        Long shopVersion,   // 修改后的版本
        ShopDTO shop        // 修改后的完整店铺数据
) {
}
