from pydantic import BaseModel, ConfigDict, Field


class NoteDraftRequest(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    material:str=Field(min_length=1,max_length=10000)
    shop_id:int | None=Field(default=None,alias="shopId",gt=0)

class NoteDraft(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)
    title: str = Field(min_length=1, max_length=60)
    content: str = Field(min_length=1, max_length=10000)

class NoteDraftResponse(BaseModel):
    success: bool = True
    data: NoteDraft