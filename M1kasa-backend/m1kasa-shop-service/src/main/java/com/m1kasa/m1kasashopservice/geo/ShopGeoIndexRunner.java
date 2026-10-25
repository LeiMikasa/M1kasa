package com.m1kasa.m1kasashopservice.geo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.m1kasa.m1kasashopservice.entity.Shop;
import com.m1kasa.m1kasashopservice.mapper.ShopMapper;
import com.m1kasa.m1kasashopservice.mapper.ShopTypeMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("geo-rebuild")
public class ShopGeoIndexRunner implements ApplicationRunner {
    private final ShopMapper shopMapper;
    private final ShopTypeMapper shopTypeMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public ShopGeoIndexRunner(ShopMapper shopMapper, ShopTypeMapper shopTypeMapper, StringRedisTemplate stringRedisTemplate) {
        this.shopMapper = shopMapper;
        this.shopTypeMapper = shopTypeMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        //查询所有类型
        shopTypeMapper.selectList(null).forEach(
                shopType -> {
                    //查询对应类型的店铺
                    List<Shop> shops=shopMapper.selectList(
                            new LambdaQueryWrapper<Shop>()
                                    .eq(Shop::getTypeId,shopType.getId())

                    );
                    List<RedisGeoCommands.GeoLocation<String>> locations = shops.stream()
                            .filter(shop -> shop.getX() != null && shop.getY() != null)
                            .map(shop -> new RedisGeoCommands.GeoLocation<>(
                                    shop.getId().toString(),
                                    new Point(shop.getX(), shop.getY())
                            )).toList();
                    String key=ShopGeoKeys.byTypes(shopType.getId());
                    stringRedisTemplate.delete(key);
                    if (!locations.isEmpty()){
                        stringRedisTemplate.opsForGeo().add(key,locations);
                    }
                    System.out.println(key+"已加载"+locations.size()+" 家店铺");
                });
    }
}
