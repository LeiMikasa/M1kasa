package com.m1kasa.m1kasasearchservice.service.impl;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasasearchservice.service.ShopImportService;
import com.m1kasa.m1kasasearchservice.service.ShopIndexService;
import com.m1kasa.shop.api.ShopDTO;
import com.m1kasa.shop.api.client.ShopClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class ShopImportServiceImpl implements ShopImportService {
    private static final Logger log=
            LoggerFactory.getLogger(ShopImportServiceImpl.class);

    private static final int BATCH_SIZE=200;
    private final ShopClient shopClient;
    private final ShopIndexService shopIndexService;

    public ShopImportServiceImpl(ShopClient shopClient, ShopIndexService shopIndexService) {
        this.shopClient = shopClient;
        this.shopIndexService = shopIndexService;
    }


    @Override
    public long importAll() throws IOException {
        long afterId=0;
        long total=0;
        while (true){
            Result<List<ShopDTO>> reply = shopClient.scanForSearch(afterId, BATCH_SIZE);
            if (reply==null || !Boolean.TRUE.equals(reply.success())){
                throw new IllegalStateException(
                        "拉取店铺失败: "+
                                (reply == null
                        ? "未返回响应"
                        : reply.errorMsg())
                );
            }
            List<ShopDTO> shops = reply.data();
            if (shops==null){
                throw new IllegalStateException(
                        "店铺 RPC 返回的列表为 null"
                );
            }
            // 2. 空列表表示后面没有店铺了，结束导入
            if (shops.isEmpty()) {
                log.info("店铺导入完成，处理数量={}", total);
                return total;
            }
            ShopDTO last = shops.get(shops.size() - 1);
            if (last==null || last.id()==null || last.id()<=afterId){
                throw new IllegalStateException(
                        "店铺 RPC 返回的 ID 游标不合法"
                );
            }
            long nextAfterId = last.id();

            // 3. 把这一批店铺写入 ES
            // 发生异常会直接退出方法，不执行后面的游标更新
            int written = shopIndexService.saveBatch(shops);

            // 4. 只有写入成功，才累计数量、推进游标
            total += written;
            afterId = nextAfterId;

            log.info(
                    "店铺导入进度，本批数量={}，累计数量={}，当前游标={}",
                    written,
                    total,
                    afterId
            );
        }
    }
}
