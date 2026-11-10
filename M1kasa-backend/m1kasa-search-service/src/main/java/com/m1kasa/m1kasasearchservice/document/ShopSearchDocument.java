package com.m1kasa.m1kasasearchservice.document;

/**
 * 一家店铺在 Elasticsearch 中的搜索数据。
 *
 * 字段名称需要与索引 mapping 中定义的名称一致。
 */
public record ShopSearchDocument(

        // 店铺 ID，对应 ES 的 keyword 字段
        String id,

        // 分类 ID，用于按分类精确筛选
        String typeId,

        // 店铺名称，用于全文搜索
        String name,

        // 图片地址，沿用原项目的字符串格式
        String images,

        // 所属区域，用于搜索或区域筛选
        String area,

        // 详细地址，用于全文搜索
        String address,

        // 经纬度，用于附近搜索、距离排序
        ShopLocation location,

        // 人均价格，沿用原项目的单位
        Long avgPrice,

        // 销量
        Integer sold,

        // 评论数量
        Integer comments,

        // 店铺评分，沿用原规则：45 表示 4.5 分
        Integer score,

        // 营业时间，仅用于展示
        String openHours
) {
}
