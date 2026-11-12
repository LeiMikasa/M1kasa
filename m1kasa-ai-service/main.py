from fastapi import FastAPI
from schemas import NoteDraftRequest,NoteDraftResponse,NoteDraft
from draft_workflow import draft_graph
app=FastAPI(title="M1kasa AI Service")

@app.post("/ai/notes/draft")
async def create_note_draft(request: NoteDraftRequest) -> NoteDraftResponse:
    result = await draft_graph.ainvoke({
        "material": request.material,
        "shop_id": request.shop_id,
    })
    return NoteDraftResponse(data=result["draft"])