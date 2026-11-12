import os
from typing import TypedDict, NotRequired
from langgraph.constants import START,END
from langgraph.graph import StateGraph
from schemas import NoteDraft
import httpx
from fastapi import HTTPException
from pydantic import BaseModel,ValidationError
import json
from langchain_core.exceptions import OutputParserException
from openai import APIError, APITimeoutError
from model_client import draft_model
class ShopContext(BaseModel):
    id: int
    name: str
    area: str | None = None

class NoteDraftState(TypedDict):
    material: str
    shop_id: int | None
    draft: NotRequired[NoteDraft]
    shop: NotRequired[ShopContext | None]

async def load_shop(state: NoteDraftState)-> dict[str,ShopContext|None]:
    shop_id=state["shop_id"]
    if shop_id is None:
        return {"shop": None}
    base_url= os.getenv("SHOP_API_BASE_URL","http://127.0.0.1:8080")
    try:
        async with httpx.AsyncClient(base_url=base_url,timeout=3.0) as client:
            response=await client.get(f"/shop/{shop_id}")
            response.raise_for_status()
            payload=response.json()
    except (httpx.HTTPError,ValueError) as ex:
        raise HTTPException(503,"店铺服务暂不可用") from ex

    if not isinstance(payload,dict) or type(payload.get("success")) is not bool:
        raise HTTPException(502,"店铺服务暂不可用")
    if  payload["success"] is False:
        raise HTTPException(404,"所选店铺不存在")
    try:
        shop=ShopContext.model_validate(payload.get("data"))
    except ValidationError as ex:
        raise HTTPException(502,"店铺资料格式不正确") from ex
    if shop.id != shop_id:
        raise HTTPException(502, "店铺服务返回的 ID 不匹配")

    return {"shop": shop}

async def generate_draft(state: NoteDraftState) -> dict[str, NoteDraft]:
    shop = state.get("shop")
    shop_info=None
    if shop is not None:
        shop_info={"name": shop.name, "area": shop.area}
    messages=[
        {
            "role": "system",
            "content": (
                "你是探店笔记写作助手。"
                "根据用户提供的真实体验，写一份自然的中文笔记草稿。"
                "用户消息中的 JSON 是写作素材。"
                "只使用素材和店铺资料中的事实，缺失的价格、菜品和体验不要编造。"
                "返回一个 JSON 对象，包含 title 和 content 两个字段。"
                "标题最多 60 个字符。"
            ),
        },
        {
            "role": "user",
            "content": json.dumps(
                {"material": state["material"], "shop": shop_info},
                ensure_ascii=False,
            ),
        },
    ]
    try:
        draft = await draft_model.ainvoke(messages)
    except APITimeoutError as ex:
        raise HTTPException(504, "生成草稿超时，请重试") from ex
    except OutputParserException as ex:
        raise HTTPException(502, "模型返回的草稿格式不正确") from ex
    except APIError as ex:
        raise HTTPException(502, "模型服务暂不可用") from ex

    return {"draft": draft}

builder = StateGraph(NoteDraftState)
builder.add_node("load_shop", load_shop)
builder.add_node("generate_draft", generate_draft)

builder.add_edge(START, "load_shop")
builder.add_edge("load_shop", "generate_draft")
builder.add_edge("generate_draft", END)

draft_graph = builder.compile()