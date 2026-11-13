from fastembed import TextEmbedding
import json
comment = "还可以吧我不懂你们在叫什么，明明服务就是很好啊"

model = TextEmbedding(model_name="BAAI/bge-small-zh-v1.5")
vector = next(iter(model.embed([comment])))

print("评论原文：", comment)
print("向量维度：", len(vector))
print("前 8 个数字：", vector[:8].tolist())

# 用 SQL 查出来的同一行数据替换这三个示例 ID
comment_id = "8"
blog_id = "8"
shop_id = "10"

document = {
    "commentId": comment_id,
    "blogId": blog_id,
    "shopId": shop_id,
    "content": comment,
    "embedding": vector.tolist(),
}

print(f"PUT /m1kasa_shop_review_v1/_doc/{comment_id}?refresh=wait_for")
print(json.dumps(document, ensure_ascii=False))