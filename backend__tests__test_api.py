from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health():
    r = client.get("/v1/health")
    assert r.status_code == 200
    assert r.json()["assistant"] == "Cyrus"


def test_chat_and_memory():
    r = client.post("/v1/chat/completions", json={"prompt": "Hello Cyrus", "mode": "local"})
    assert r.status_code == 200
    assert "Cyrus Backend" in r.json()["reply"]

    m = client.post("/v1/memories", json={"content": "User prefers concise answers", "category": "preference"})
    assert m.status_code == 200
    assert m.json()["category"] == "preference"
