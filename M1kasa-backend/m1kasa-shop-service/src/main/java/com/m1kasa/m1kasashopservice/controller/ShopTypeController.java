package com.m1kasa.m1kasashopservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasashopservice.service.ShopTypeService;
import com.m1kasa.m1kasashopservice.service.impl.ShopTypeServiceImpl;
import com.m1kasa.shop.api.ShopTypeDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    private final ShopTypeService shopTypeService;

    public ShopTypeController(ShopTypeService shopTypeService){
        this.shopTypeService=shopTypeService;
    }

    @GetMapping("list")
    public Result<List<ShopTypeDTO>> list(){
        List<ShopTypeDTO> types=shopTypeService.listTypes();
        return types.isEmpty() ? Result.fail("不存在"):Result.ok(types);
    }
}
