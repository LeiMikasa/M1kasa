from langchain_deepseek import ChatDeepSeek

from schemas import NoteDraft

model=ChatDeepSeek(
    model="deepseek-flash",
    timeout=20,
    max_retries=0,
    extra_body={"thinking": {"type": "disabled"}},
)

draft_model=model.with_structured_output(NoteDraft,method="json_mode")