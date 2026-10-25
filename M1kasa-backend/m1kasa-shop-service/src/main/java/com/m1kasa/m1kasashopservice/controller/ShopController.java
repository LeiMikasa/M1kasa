package com.m1kasa.m1kasashopservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasashopservice.entity.Shop;
import com.m1kasa.m1kasashopservice.service.ShopService;
import com.m1kasa.m1kasashopservice.web.ShopCreateRequest;
import com.m1kasa.m1kasashopservice.web.ShopUpdateRequest;
import com.m1kasa.shop.api.ShopDTO;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/shop")
public class ShopController {
    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    private final ShopService shopService;
    @GetMapping("/{id}")
    public Result<ShopDTO> getById(@PathVariable Long id){
        ShopDTO shop=shopService.getById(id);
        return shop==null?Result.fail("店铺不存在"):Result.ok(shop);
    }

    @PutMapping
    public Result<Void> update(@RequestBody ShopUpdateRequest request){
        if (request.id()==null){
            return Result.fail("店铺id不能为空");
        }
        Shop shop=new Shop();
        shop.setId(request.id());
        shop.setName(request.name());
        shop.setTypeId(request.typeId());
        shop.setImages(request.images());
        shop.setArea(request.area());
        shop.setAddress(request.address());
        shop.setX(request.x());
        shop.setY(request.y());
        shop.setAvgPrice(request.avgPrice());
        shop.setOpenHours(request.openHours());

        return shopService.update(shop)?
         Result.ok():Result.fail("店铺不存在");
    }

    @GetMapping("/of/type")
    public Result<List<ShopDTO>> listByType(
            @RequestParam Long typeId,
            @RequestParam(defaultValue = "1")long current,
            @RequestParam(required = false) Double x,
            @RequestParam(required = false) Double y
    ){
        if (typeId<=0 || current<=0){
            return Result.fail("分类id和页码必须大于0");
        }
        if ((x==null)!=(y==null)){
            return Result.fail("经度和纬度必须同时提供");
        }
        if (x == null) {
            return Result.ok(shopService.listByType(typeId, current));
        }
        if (current>100){
            return Result.fail("附近店铺最多查询前100页");
        }
        return Result.ok(shopService.listNearByType(typeId,current,x,y));
    }

    @PostMapping
    public Result<Long> create(@RequestBody ShopCreateRequest request){
        if (request.name()==null || request.name().isBlank()
        || request.typeId()==null || request.typeId()<=0
        ||request.images()==null ||request.address()==null ||
        request.address().isBlank()||request.x()==null||request.y()==null){
            return Result.fail("店铺名称，分类，图片，地址和坐标不能为空");
        }
        Shop shop = new Shop();
        shop.setName(request.name());
        shop.setTypeId(request.typeId());
        shop.setImages(request.images());
        shop.setArea(request.area());
        shop.setAddress(request.address());
        shop.setX(request.x());
        shop.setY(request.y());
        shop.setAvgPrice(request.avgPrice());
        shop.setOpenHours(request.openHours());

        return Result.ok(shopService.create(shop));
    }

    //根据名称分页
    @GetMapping("/of/name")
    public Result<List<ShopDTO>> searchByName(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "1") long current
    ){
        if (current<=0){
            return Result.fail("页码必须大于 0");
        }
        return Result.ok(shopService.searchByName(name,current));
    }

}
