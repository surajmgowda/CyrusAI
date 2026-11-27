from fastapi import FastAPI
from app.api.routes import router

app = FastAPI(
    title="Cyrus Personal AI Backend",
    version="1.0.0",
    description="Optional hybrid FastAPI service for Cyrus Android 17+ Assistant"
)
app.include_router(router, prefix="/v1")
