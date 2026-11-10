package com.m1kasa.m1kasasearchservice.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.m1kasa.m1kasasearchservice.document.ShopSearchDocument;
import com.m1kasa.m1kasasearchservice.dto.ShopSearchPageDTO;
import com.m1kasa.m1kasasearchservice.service.ShopSearchService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

@Service
public class ShopSearchServiceImpl implements ShopSearchService {

    private static final String INDEX_ALIAS = "m1kasa_shop";

    private final ElasticsearchClient elasticsearchClient;

    public ShopSearchServiceImpl(
            ElasticsearchClient elasticsearchClient
    ) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public ShopSearchPageDTO search(
            String keyword,
            Long typeId,
            int current,
            int size
    ) throws IOException {

        // bool 用于组合多个查询条件
        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (StringUtils.hasText(keyword)) {

            // 有关键词：同时搜索店铺名称、区域、地址
            //添加查询条件
            bool.must(query -> query.multiMatch( //多个字段搜索
                    match -> match
                            .query(keyword.trim()) //关键字搜索
                            .fields(    //选择查询的字段
                                    "name^3",
                                    "name.partial^2",
                                    "name.pinyin",
                                    "area^2",
                                    "address"
                            )
            ));

        } else {

            // 没有关键词：先匹配全部店铺
            bool.must(query -> query.matchAll(
                    match -> match
            ));
        }

        if (typeId != null) {

            // 分类是 keyword 字段，使用 term 精确匹配
            bool.filter(query -> query.term(
                    term -> term
                            .field("typeId")
                            .value(typeId.toString())
            ));
        }

        // 页码从 1 开始，ES 的 from 表示跳过多少条
        int from = Math.toIntExact((current - 1L) * size);

        SearchRequest.Builder request = new SearchRequest.Builder()
                .index(INDEX_ALIAS)
                .query(query -> query.bool(bool.build()))
                .from(from)
                .size(size)

                // 要求返回准确的匹配总数
                .trackTotalHits(total -> total.enabled(true))

                // 有分片查询失败时，让请求失败
                .allowPartialSearchResults(false)

                // 首先按搜索相关度从高到低排序
                .sort(sort -> sort.score(
                        score -> score.order(SortOrder.Desc)
                ))

                // 相关度相同时，再按 id 排序
                .sort(sort -> sort.field(
                        field -> field
                                .field("id")
                                .order(SortOrder.Asc)
                ));

        // 此处才真正向 ES 发送搜索请求
        SearchResponse<ShopSearchDocument> response =
                elasticsearchClient.search(
                        request.build(),
                        ShopSearchDocument.class
                );

        if (response.timedOut()) {
            throw new IllegalStateException("店铺搜索超时");
        }

        // 从每条命中结果中取出店铺文档
        List<ShopSearchDocument> records = response
                .hits()
                .hits()
                .stream()
                .map(Hit::source)
                .filter(Objects::nonNull)
                .toList();

        TotalHits total = response.hits().total();

        if (total == null) {
            throw new IllegalStateException("ES 未返回匹配总数");
        }

        return new ShopSearchPageDTO(
                records,
                total.value(),
                current,
                size
        );
    }
}
