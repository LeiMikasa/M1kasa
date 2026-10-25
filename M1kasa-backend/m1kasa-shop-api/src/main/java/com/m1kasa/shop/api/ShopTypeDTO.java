package com.m1kasa.shop.api;

public record ShopTypeDTO(
        Long id,
        String name,
        String icon,
        Integer sort
) { }
