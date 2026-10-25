package com.m1kasa.m1kasashopservice.web;

public record ShopUpdateRequest(
        Long id,
        String name,
        Long typeId,
        String images,
        String area,
        String address,
        Double x,
        Double y,
        Long avgPrice,
        String openHours
) {
}
