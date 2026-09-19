import json
import os
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("UPDATER_TOKEN", "")
REPO = os.environ.get("REPOSITORY_DIR", "/opt/ffh_verwaltung")
GITHUB_REPOSITORY = os.environ.get("GITHUB_REPOSITORY", "kesselbuettner-dot/ffh_Verwaltung")
PORT = 8091
_lock = threading.Lock()
_state = {"status": "idle", "message": "Bereit", "target": None}

def run(cmd):
    return subprocess.run(cmd, cwd=REPO, text=True, capture_output=True, check=True)

def latest_release():
    p = subprocess.run(
        ["curl", "-fsSL", "-H", "Accept: application/vnd.github+json",
         f"https://api.github.com/repos/{GITHUB_REPOSITORY}/releases/latest"],
        text=True, capture_output=True, check=True
    )
    data = json.loads(p.stdout)
    return data["tag_name"]

def set_state(status, message, target=None):
    _state.update(status=status, message=message, target=target)

def update():
    with _lock:
        try:
            set_state("running", "GitHub-Release wird ermittelt")
            tag = latest_release()
            set_state("running", f"Update auf {tag}", tag)
            run(["git", "fetch", "--tags", "--force", "--prune", "origin"])
            run(["git", "checkout", "--detach", tag])

            env_path = os.path.join(REPO, ".env")
            if os.path.exists(env_path):
                lines = open(env_path, encoding="utf-8").read().splitlines()
                found = False
                for i, line in enumerate(lines):
                    if line.startswith("APP_VERSION="):
                        lines[i] = f"APP_VERSION={tag.lstrip('v')}"
                        found = True
                        break
                if not found:
                    lines.append(f"APP_VERSION={tag.lstrip('v')}")
                open(env_path, "w", encoding="utf-8").write("\n".join(lines) + "\n")

            run(["docker", "compose", "up", "-d", "--build", "--remove-orphans",
                 "app", "worker", "cloudflared"])
            set_state("success", f"Update auf {tag} abgeschlossen", tag)
        except Exception as exc:
            set_state("error", str(exc))
            raise

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)

    def reply(self, code, payload):
        raw = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def authorized(self):
        return TOKEN and self.headers.get("X-Updater-Token", "") == TOKEN

    def do_GET(self):
        if self.path == "/health":
            self.reply(200, {"status": _state["status"], "message": _state["message"], "target": _state["target"]})
            return
        self.reply(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/update":
            self.reply(404, {"error": "not found"})
            return
        if not self.authorized():
            self.reply(401, {"error": "unauthorized"})
            return
        if _lock.locked():
            self.reply(409, {"status": "running", "message": "Ein Update läuft bereits"})
            return
        threading.Thread(target=self._run_update, daemon=True).start()
        self.reply(202, {"status": "accepted", "message": "Update wurde gestartet"})

    def _run_update(self):
        try:
            update()
        except Exception:
            pass

ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
