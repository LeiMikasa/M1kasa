package com.m1kasa.m1kasasearchservice.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import com.m1kasa.m1kasasearchservice.document.ShopLocation;
import com.m1kasa.m1kasasearchservice.document.ShopSearchDocument;
import com.m1kasa.m1kasasearchservice.service.ShopIndexService;
import com.m1kasa.shop.api.ShopDTO;
import com.m1kasa.shop.api.message.ShopSearchSyncMessage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class ShopIndexServiceImpl implements ShopIndexService {
    private static final String INDEX_ALIAS="m1kasa_shop";
    private final ElasticsearchClient elasticsearchClient;

    public ShopIndexServiceImpl(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    @Override
    public int saveBatch(List<ShopDTO> shops) throws IOException {
        if (shops==null){
            throw new IllegalArgumentException("店铺列表不能为null");
        }
        if (shops.isEmpty()){
            return 0;
        }
        //创建批量请求，指定统一的目标索引别名
        BulkRequest.Builder builder = new BulkRequest.Builder()
                .index(INDEX_ALIAS)
                .requireAlias(true);
        for (ShopDTO shop : shops) {

            ShopSearchDocument document = toDocument(shop);

            if (shop.searchVersion() == null || shop.searchVersion() < 1) {
                throw new IllegalArgumentException("店铺搜索版本不合法");
            }

            builder.operations(operation -> operation.index(
                    index -> index
                            .id(document.id())

                            // 使用 MySQL 中的版本
                            .version(shop.searchVersion())

                            // 只有传入版本更大，才允许写入
                            .versionType(VersionType.External)

                            .document(document)
            ));
        }
        //构建请求并发送
        BulkResponse response = elasticsearchClient.bulk(builder.build());
        if (response.errors()) {

            for (BulkResponseItem item : response.items()) {

                if (item.error() == null) {
                    continue;
                }

                // ES 已有相同或更新版本，可以安全跳过
                if (item.status() == 409
                        && "version_conflict_engine_exception"
                        .equals(item.error().type())) {
                    continue;
                }

                // 其他错误继续抛出，不能假装导入成功
                throw new IllegalStateException(
                        "店铺写入 ES 失败，id="
                                + item.id()
                                + "，原因="
                                + item.error().reason()
                );
            }
        }
        return shops.size();
    }

    @Override
    public void sync(ShopSearchSyncMessage message) throws IOException {

        // 先检查消息，避免将错误数据写到其他店铺
        if (message == null
                || message.requestId() == null
                || message.requestId().isBlank()
                || message.shopId() == null
                || message.shopId() <= 0
                || message.shopVersion() == null
                || message.shopVersion() < 1
                || message.shop() == null
                || !message.shopId().equals(message.shop().id())) {

            throw new IllegalArgumentException("店铺搜索同步消息不合法");
        }

        ShopSearchDocument document = toDocument(message.shop());

        try {
            elasticsearchClient.index(
                    index -> index
                            .index(INDEX_ALIAS)
                            .requireAlias(true)
                            .id(document.id())

                            // 使用消息中保存的版本
                            .version(message.shopVersion())
                            .versionType(VersionType.External)

                            .document(document)
            );

        } catch (ElasticsearchException ex) {

            // 相同版本表示重复消息，更低版本表示旧消息
            if (ex.status() == 409
                    && "version_conflict_engine_exception"
                    .equals(ex.error().type())) {

                return;
            }

            // 其他 ES 错误继续抛给消费者，由消费重试处理
            throw ex;
        }
    }

    /*
    * 把rpc返回的店铺DTO转换乘ES文档
    * */
    private ShopSearchDocument toDocument(ShopDTO shop){
        if (shop==null || shop.id()==null || shop.id()<=0){
            throw new IllegalArgumentException("店铺ID不合法");
        }
        //没有完整坐标时，整个位置字段保持为null
        ShopLocation location=null;
        if (shop.x() != null && shop.y() != null) {
            location = new ShopLocation(
                    shop.x(),  // 经度
                    shop.y()   // 纬度
            );
        }

        return new ShopSearchDocument(
                shop.id().toString(),
                shop.typeId()==null
                ?null:shop.typeId().toString(),
                shop.name(),
                shop.images(),
                shop.area(),
                shop.address(),
                location,
                shop.avgPrice(),
                shop.sold(),
                shop.comments(),
                shop.score(),
                shop.openHours()
        );
    }
}
