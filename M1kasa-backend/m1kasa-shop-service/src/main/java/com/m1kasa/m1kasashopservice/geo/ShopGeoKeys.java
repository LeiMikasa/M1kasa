package com.m1kasa.m1kasashopservice.geo;

public final class ShopGeoKeys {
    private ShopGeoKeys(){}

    public static String byTypes(Long typeId){
        return "m1kasa:shop:geo"+typeId;
    }
}
