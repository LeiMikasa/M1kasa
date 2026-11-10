package com.m1kasa.m1kasasearchservice.controller;


import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasasearchservice.service.ShopImportService;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@Profile("local")
@RestController
@RequestMapping("/internal/search")
public class ShopImportController {
    private final ShopImportService shopImportService;


    public ShopImportController(ShopImportService shopImportService) {
        this.shopImportService = shopImportService;
    }
    @PostMapping("/shop-import")
    public Result<Long> importAll() throws IOException{
        long total = shopImportService.importAll();
        return Result.ok(total);
    }
}
