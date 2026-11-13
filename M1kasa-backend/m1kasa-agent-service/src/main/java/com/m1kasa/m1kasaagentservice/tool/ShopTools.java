package com.m1kasa.m1kasaagentservice.tool;

import com.m1kasa.common.dto.Result;
import com.m1kasa.search.api.client.ReviewSearchClient;
import com.m1kasa.search.api.dto.ReviewHit;
import com.m1kasa.search.api.dto.ReviewSearchRequest;
import com.m1kasa.shop.api.ShopDTO;
import com.m1kasa.shop.api.ShopTypeDTO;
import com.m1kasa.shop.api.client.ShopClient;
import feign.FeignException;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ShopTools {

    private final ShopClient shopClient;
    private final ReviewSearchClient reviewSearchClient;
    public ShopTools(ShopClient shopClient, ReviewSearchClient reviewSearchClient) {
        this.shopClient = shopClient;
        this.reviewSearchClient = reviewSearchClient;
    }

    @Tool(name = "getShopDetail", description = "根据店铺 ID 查询店铺名称和地址")
    public String getShopDetail(
            @ToolParam(description = "要查询的店铺 ID") long shopId
    ) {
        if (shopId <= 0) {
            return "店铺 ID 必须大于 0。";
        }

        Result<ShopDTO> result;
        try {
            result = shopClient.getById(shopId);
        } catch (FeignException ex) {
            return "店铺服务暂时无法查询，请稍后重试。";
        }

        if (result == null || !Boolean.TRUE.equals(result.success()) || result.data() == null) {
            return "未查到 ID 为 " + shopId + " 的店铺。";
        }

        ShopDTO shop = result.data();
        String address = shop.address();
        if (address == null || address.isBlank()) {
            return "店铺名称：" + shop.name() + "，暂无地址信息。";
        }

        return "店铺名称：" + shop.name() + "，地址：" + address;
    }

    @Tool(
            name = "findNearbyShops",
            description = "按用户位置查询附近5公里内的指定分类的店铺，按距离返回第一页，需要已知分类ID"
    )
    public String findNearbyShops(
            @ToolParam(description = "已知的店铺分类ID") long typeId,
            ToolContext toolContext
    ){
        if (typeId<=0){
            return "请提供有效的店铺分类ID";
        }
        Double x=(Double) toolContext.getContext().get("longitude");
        Double y=(Double) toolContext.getContext().get("latitude");
        if (x==null||y==null){
            return "当前没有用户位置，请用户提供或授权定位。";
        }
        if (!(Math.abs(x)<=180&&Math.abs(y)<=85.05112878)){
            return "当前位置的经纬度超出附近查询支持的范围";
        }
        Result<List<ShopDTO>> result;
        try {
            result=shopClient.findNearbyByType(typeId,1,x,y);
        }catch (FeignException ex){
            return "附近店铺暂时无法查询,请稍后重试";
        }
        if (result==null||!Boolean.TRUE.equals(result.success())||result.data()==null){
            return "附近店铺查询失败";
        }
        if (result.data().isEmpty()){
            return "当前位置附近5公里内没有查到该分类的店铺";
        }
        return result.data().stream()
                .map(shop -> "店铺 " + shop.id() + "：" + shop.name()
                        + "，地址：" + shop.address()
                        + "，距离约 " + Math.round(shop.distance()) + " 米")
                .collect(Collectors.joining("\n"));
    }
    @Tool(
            name = "listShopTypes",
            description = "查询平台的店铺分类名称和 ID，用于把用户说的分类转换为查询附近店铺所需的分类 ID"
    )
    public String listShopTypes() {
        Result<List<ShopTypeDTO>> result;
        try {
            result = shopClient.listShopTypes();
        } catch (FeignException ex) {
            return "店铺分类暂时无法查询，请稍后重试。";
        }

        if (result == null || !Boolean.TRUE.equals(result.success()) || result.data() == null) {
            return "店铺分类查询失败。";
        }

        return result.data().stream()
                .map(type -> type.name() + "，分类 ID：" + type.id())
                .collect(Collectors.joining("\n"));
    }

    @Tool(
            name = "searchNearbyShopReview",
            description = "查询当前位置附近指定分类的店铺评论，适合回答口味，环境，排队和服务等问题"
    )
    public String searchNearbyShopReviews(
        @ToolParam(description = "已知的店铺分类ID")long typeId,
        @ToolParam(description = "用户关心的问题，例如上菜快不快")String question,
        ToolContext context
    ){
        if (typeId<=0||question==null||question.isBlank()){
            return "请提供有效的店铺分类和想了解的问题";
        }
        Double x=(Double) context.getContext().get("longitude");
        Double y=(Double) context.getContext().get("latitude");
        if (x==null||y==null){
            return "请先提供当前问题";
        }
        if (!(Math.abs(x) <= 180 && Math.abs(y) <= 85.05112878)) {
            return "当前位置超出附近查询支持的范围。";
        }
        try {
            Result<List<ShopDTO>> nearby = shopClient.findNearbyByType(typeId, 1, x, y);
            if (nearby==null||!Boolean.TRUE.equals(nearby.success())
        || nearby.data()==null){
                return "附近店铺查询失败";
            }
            if (nearby.data().isEmpty()){
                return "附近没有查到这个分类的店铺";
            }
            List<Long> shopIds = nearby.data().stream().map(ShopDTO::id).toList();
            Result<List<ReviewHit>> reviews = reviewSearchClient.search(
                    new ReviewSearchRequest(question, shopIds)
            );
            if (reviews==null||!Boolean.TRUE.equals(reviews.success())
            || reviews.data()==null){
                return "评论检索失败";
            }
            if (reviews.data().isEmpty()){
                return "这些店铺暂时没有检索到任何可参考的评论。";
            }
            String shops = nearby.data().stream()
                    .map(shop -> shop.id() + " " + shop.name())
                    .collect(Collectors.joining("\n"));
            String evidence = reviews.data().stream()
                    .map(review -> "店铺 " + review.shopId()
                            + "，笔记 " + review.blogId()
                            + "，评论 " + review.commentId()
                            + "：" + review.content())
                    .collect(Collectors.joining("\n"));
            return "候选店铺：\n" + shops + "\n评论摘录：\n" + evidence;
        }catch (FeignException ex) {
            return "附近店铺或评论服务暂时无法查询，请稍后重试。";
        }
    }
}
