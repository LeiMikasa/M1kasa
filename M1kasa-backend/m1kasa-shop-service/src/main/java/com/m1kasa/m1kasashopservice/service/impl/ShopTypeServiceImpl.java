package com.m1kasa.m1kasashopservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.m1kasa.m1kasashopservice.entity.ShopType;
import com.m1kasa.m1kasashopservice.mapper.ShopTypeMapper;
import com.m1kasa.m1kasashopservice.service.ShopTypeService;
import com.m1kasa.shop.api.ShopTypeDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

@Service
public class ShopTypeServiceImpl implements ShopTypeService {
    private final ShopTypeMapper shopTypeMapper;
    private static final String CACHE_KEY="m1kasa:shop:type:list";
    private final StringRedisTemplate stringRedisTemplate;
    private final JsonMapper jsonMapper;
    private static final TypeReference<List<ShopTypeDTO>> LIST_TYPE =
            new TypeReference<>() {};
    public ShopTypeServiceImpl(ShopTypeMapper shopTypeMapper,
                               StringRedisTemplate stringRedisTemplate,
                               JsonMapper jsonMapper){
        this.shopTypeMapper=shopTypeMapper;
        this.stringRedisTemplate=stringRedisTemplate;
        this.jsonMapper=jsonMapper;
    }
    @Override
    public List<ShopTypeDTO> listTypes(){
        String cached=stringRedisTemplate.opsForValue().get(CACHE_KEY);
        if (cached!=null){
            return jsonMapper.readValue(cached,LIST_TYPE);
        }
        List<ShopTypeDTO> types=shopTypeMapper.selectList(
                new LambdaQueryWrapper<ShopType>()
                        .orderByAsc(ShopType::getSort)
        ).stream().map(
                type->new ShopTypeDTO(
                        type.getId(),type.getName(),type.getIcon(),
                        type.getSort()
                )
        ).toList();
        Duration ttl=types.isEmpty()?Duration.ofMinutes(2):Duration.ofMinutes(30);
        stringRedisTemplate.opsForValue().set(CACHE_KEY,jsonMapper.writeValueAsString(types),ttl);
        return types;
    }
}
