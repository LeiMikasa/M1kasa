# ES 店铺搜索：后端接入步骤

前端已改为通过网关调用 `GET /search/shops`，后端代码继续由你手写。本次检查发现搜索 Controller、网关路由和公开访问规则尚未添加，按以下步骤补齐。

## 1. 添加搜索 Controller

文件：`D:\M1kasa\M1kasa-backend\m1kasa-search-service\src\main\java\com\m1kasa\m1kasasearchservice\controller\ShopSearchController.java`

```java
package com.m1kasa.m1kasasearchservice.controller;

import com.m1kasa.common.dto.Result;
import com.m1kasa.m1kasasearchservice.dto.ShopSearchPageDTO;
import com.m1kasa.m1kasasearchservice.service.ShopSearchService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/search/shops")
public class ShopSearchController {

    private final ShopSearchService shopSearchService;

    public ShopSearchController(ShopSearchService shopSearchService) {
        this.shopSearchService = shopSearchService;
    }

    @GetMapping
    public Result<ShopSearchPageDTO> search(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "typeId", required = false) Long typeId,
            @RequestParam(name = "current", defaultValue = "1") int current,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) throws IOException {

        // 校验分页参数，避免无效请求和过大的单页数据。
        if (current < 1 || size < 1 || size > 50) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "页码必须大于 0，每页数量必须在 1 到 50 之间"
            );
        }
        if (typeId != null && typeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分类 ID 必须大于 0");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "搜索关键词不能超过 100 个字符");
        }

        // 使用 long 计算偏移，并限制在当前 ES 默认分页窗口内。
        long from = (current - 1L) * size;
        if (from + size > 10_000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前搜索最多支持查看前 10000 条结果");
        }

        // 调用已经写好的搜索 Service，并使用项目统一响应格式。
        return Result.ok(shopSearchService.search(keyword, typeId, current, size));
    }
}
```

这个 Controller 把 URL 中的关键词、分类和分页参数交给已有 Service。`typeId` 可选；当前前端的关键词搜索不默认限定分类。此接口是正式的公开搜索入口，不加 `@Profile("local")`。

## 2. 添加网关路由

文件：`D:\M1kasa\M1kasa-backend\m1kasa-gateway\src\main\resources\application.yaml`

在现有 `spring.cloud.gateway.server.webflux.routes` 数组中，和店铺、用户等路由平级增加：

```yaml
            # 将公开店铺搜索请求转发给搜索服务。
            - id: search-service
              uri: http://localhost:8087
              predicates:
                - Path=/search/shops
```

现有网关路由使用固定的本机地址，因此这里保持同样方式。请求路径原样转发，不设置 StripPrefix。只开放公开搜索路径；本地导入、连接检查等 `/internal/search/**` 路径不在这条路由中。

如果你还维护 `application.example.yaml`，在它的 routes 中加入相同片段。

## 3. 允许匿名 GET 搜索

文件：`D:\M1kasa\M1kasa-backend\m1kasa-gateway\src\main\java\com\m1kasa\m1kasagateway\filter\AuthenticationFilter.java`

在 `isPublicRequest` 方法的 GET 分支中添加一项条件：

```java
                    // 公开店铺搜索，允许未登录用户访问。
                    || "/search/shops".equals(path)
```

例如这个分支的开头可以是：

```java
        if (HttpMethod.GET.equals(method)) {
            return "/shop-type/list".equals(path)
                    || path.startsWith("/shop/")
                    || "/search/shops".equals(path)
                    // 继续保留你原有的其他公开访问条件。
                    ;
        }
```

上面是展示插入位置的片段，不要用它覆盖其他已存在的公开访问条件。只允许该路径的 GET 请求，保留其他鉴权逻辑。

## 4. 重新编译和启动

```powershell
Set-Location D:\M1kasa\M1kasa-backend

# 编译搜索服务、网关及其依赖模块。
mvn -pl "m1kasa-search-service,m1kasa-gateway" -am compile
```

在 IDEA 重启搜索服务和网关。搜索服务继续使用你已有的 Nacos、ES 连接配置。

先直接检查搜索服务：

`http://localhost:8087/search/shops?keyword=咖啡&current=1&size=10`

再检查经过网关的相同请求：

`http://localhost:8080/search/shops?keyword=咖啡&current=1&size=10`

成功响应结构为：

```text
success: true
data.records: 当前页店铺列表
data.total: 所有匹配店铺数量
data.current: 当前页码
data.size: 每页条数
```

如果响应成功但 records 为空，先确认已经执行初始化导入。在搜索服务启用 local 后，可以手动触发：

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8087/internal/search/shop-import"
```

初始化导入会通过店铺 RPC 读取 MySQL，再批量写入 ES。普通搜索直接查 ES；数据库中的后续新增、修改和删除，目前还需补增量同步，不能将这次接入当成自动同步已完成。

## 5. 前端手动验证

打开 `http://localhost:5173/`，点击首页搜索入口。

1. 未登录时可以搜索。
2. 搜索真实店铺名称中的词，也可以尝试区域、地址中的词。
3. 页面显示匹配总数，最后一页的“下一页”禁用。
4. 点击店铺卡片进入原有店铺详情，返回后保留搜索词和页码。
5. 接口不可用时显示重试入口，不把失败显示成“没有结果”，也不自动切回旧的 MySQL 名称搜索。

本次前端自动检查使用模拟接口响应。真实后端链路需完成上述手写代码并重启后，再按这些步骤确认。
