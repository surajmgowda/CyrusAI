from datetime import datetime, timezone
from typing import Dict, List, Optional
from uuid import uuid4
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter()

# In-memory store for reference backend; replace with PostgreSQL/SQLite in production.
CONVERSATIONS: Dict[str, dict] = {}
TASKS: Dict[str, dict] = {}
APPROVALS: Dict[str, dict] = {}
MEMORIES: Dict[str, dict] = {}
AUTOMATIONS: Dict[str, dict] = {}


class ChatRequest(BaseModel):
    conversation_id: Optional[str] = None
    prompt: str
    mode: str = "automatic"


class MemoryCreate(BaseModel):
    content: str
    category: str = "general"
    source_ref: str = "user"


class TaskCreate(BaseModel):
    prompt: str
    permission_scope: str = "read_only"


class ApprovalDecisionRequest(BaseModel):
    decision: str  # APPROVED | DENIED


class AutomationCreate(BaseModel):
    name: str
    trigger_config: str
    action_config: str
    enabled: bool = True


@router.get("/health")
def health():
    return {"status": "ok", "assistant": "Cyrus", "timestamp": datetime.now(timezone.utc).isoformat()}


@router.post("/auth/session")
def create_session():
    return {"token": f"cyrus_sess_{uuid4().hex}", "expires_in": 3600}


@router.post("/chat/completions")
def chat_completion(req: ChatRequest):
    cid = req.conversation_id or str(uuid4())
    if cid not in CONVERSATIONS:
        CONVERSATIONS[cid] = {"id": cid, "title": req.prompt[:40], "messages": []}
    reply = f"Cyrus Backend ({req.mode}): Received '{req.prompt}'."
    CONVERSATIONS[cid]["messages"].append({"role": "user", "content": req.prompt})
    CONVERSATIONS[cid]["messages"].append({"role": "assistant", "content": reply})
    return {"conversation_id": cid, "reply": reply, "route": req.mode}


@router.get("/conversations")
def list_conversations():
    return {"conversations": list(CONVERSATIONS.values())}


@router.post("/agents/tasks")
def create_task(req: TaskCreate):
    tid = str(uuid4())
    task = {
        "id": tid,
        "prompt": req.prompt,
        "status": "RUNNING",
        "permission_scope": req.permission_scope,
    }
    TASKS[tid] = task
    return task


@router.get("/agents/tasks/{task_id}")
def get_task(task_id: str):
    if task_id not in TASKS:
        raise HTTPException(status_code=404, detail="Task not found")
    return TASKS[task_id]


@router.post("/agents/tasks/{task_id}/cancel")
def cancel_task(task_id: str):
    if task_id not in TASKS:
        raise HTTPException(status_code=404, detail="Task not found")
    TASKS[task_id]["status"] = "CANCELLED"
    return TASKS[task_id]


@router.get("/approvals")
def list_approvals():
    return {"approvals": list(APPROVALS.values())}


@router.post("/approvals/{approval_id}/decision")
def decide_approval(approval_id: str, body: ApprovalDecisionRequest):
    if approval_id not in APPROVALS:
        APPROVALS[approval_id] = {"id": approval_id, "decision": body.decision}
    else:
        APPROVALS[approval_id]["decision"] = body.decision
    return APPROVALS[approval_id]


@router.get("/memories")
def list_memories():
    return {"memories": list(MEMORIES.values())}


@router.post("/memories")
def create_memory(req: MemoryCreate):
    mid = str(uuid4())
    item = {"id": mid, "content": req.content, "category": req.category, "source_ref": req.source_ref}
    MEMORIES[mid] = item
    return item


@router.delete("/memories/{memory_id}")
def delete_memory(memory_id: str):
    MEMORIES.pop(memory_id, None)
    return {"deleted": memory_id}


@router.get("/automations")
def list_automations():
    return {"automations": list(AUTOMATIONS.values())}


@router.post("/automations")
def create_automation(req: AutomationCreate):
    aid = str(uuid4())
    item = req.model_dump() | {"id": aid}
    AUTOMATIONS[aid] = item
    return item


@router.get("/usage")
def usage():
    return {"conversations": len(CONVERSATIONS), "tasks": len(TASKS), "memories": len(MEMORIES)}
