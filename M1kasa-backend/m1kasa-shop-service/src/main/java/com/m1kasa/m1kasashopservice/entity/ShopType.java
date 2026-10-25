package com.m1kasa.m1kasashopservice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

//商品类型实体
@TableName("tb_shop_type")
public class ShopType {
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;

    private String name;

    private String icon;

    private Integer sort;

    public Integer getSort() {
        return sort;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
