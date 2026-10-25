package com.m1kasa.shop.api;

public record ShopDTO(
        Long id,
        String name,
        Long typeId,
        String images,
        String area,
        String address,
        Double x,
        Double y,
        Long avgPrice,
        Integer sold,
        Integer comments,
        Integer score,
        String openHours,
        Double distance
) {
}
