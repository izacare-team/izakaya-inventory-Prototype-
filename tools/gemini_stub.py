"""Gemini 스텁 서버 — 재시도/타임아웃 동작 검증용.

동작 모드를 GET /_mode?m=... 으로 바꾼다. 앱은 한 번만 띄우면 된다.
  retry      첫 요청 503, 두 번째부터 200  → 재시도가 있으면 최종 성공
  always503  항상 503                      → 2회 시도 후 실패
  fail4xx    항상 400                      → 재시도 없이 1회로 실패
  hang       응답 안 줌                    → 읽기 타임아웃(15초)이 걸려야 함

요청 횟수는 GET /_stats 로 확인한다.
"""
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

PORT = 9999
state = {"mode": "ok", "count": 0}
lock = threading.Lock()

GEMINI_OK = {
    "candidates": [{
        "content": {"parts": [{"text": json.dumps([
            {"name": "생맥주", "quantity": 3, "confidence": 0.9},
            {"name": "두부", "quantity": 5, "confidence": 0.8},
        ], ensure_ascii=False)}]}
    }]
}
ERR503 = {"error": {"code": 503, "message": "The model is overloaded (high demand)"}}
ERR400 = {"error": {"code": 400, "message": "API key not valid"}}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def _send(self, code, payload):
        raw = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        u = urlparse(self.path)
        if u.path == "/_mode":
            m = parse_qs(u.query).get("m", ["ok"])[0]
            with lock:
                state["mode"] = m
                state["count"] = 0
            self._send(200, {"mode": m})
            return
        if u.path == "/_stats":
            with lock:
                self._send(200, dict(state))
            return
        self._send(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        self.rfile.read(length)
        with lock:
            state["count"] += 1
            mode, n = state["mode"], state["count"]

        if mode == "retry":
            self._send(503, ERR503) if n == 1 else self._send(200, GEMINI_OK)
        elif mode == "always503":
            self._send(503, ERR503)
        elif mode == "fail4xx":
            self._send(400, ERR400)
        elif mode == "hang":
            time.sleep(60)
        else:
            self._send(200, GEMINI_OK)


if __name__ == "__main__":
    print("stub listening on %d" % PORT, flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
