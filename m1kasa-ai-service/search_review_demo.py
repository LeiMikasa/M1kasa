import json

from fastembed import TextEmbedding

question = "服务怎么样？"
model = TextEmbedding(model_name="BAAI/bge-small-zh-v1.5")
query_vector = next(iter(model.query_embed(question))).tolist()

search = {
    "size": 3,
    "_source": ["commentId", "blogId", "shopId", "content"],
    "query": {
        "knn": {
            "field": "embedding",
            "query_vector": query_vector,
            "k": 3,
            "num_candidates": 20,
            "filter": {"term": {"shopId": "10"}},
        }
    },
}

print("POST /m1kasa_shop_review_v1/_search")
print(json.dumps(search, ensure_ascii=False))