import os
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

TOKEN = os.environ.get("UPDATER_TOKEN", "")
WORKSPACE = os.environ.get("UPDATER_WORKSPACE", "/workspace")
PORT = int(os.environ.get("UPDATER_PORT", "8090"))
REPO = os.environ.get("GITHUB_REPOSITORY", "kesselbuettner-dot/ffh_Verwaltung")

running = False
lock = threading.Lock()

def run(cmd):
    print("RUN:", " ".join(cmd), flush=True)
    subprocess.run(cmd, cwd=WORKSPACE, check=True)

def update():
    global running
    with lock:
        if running:
            print("Update already running", flush=True)
            return
        running = True

    try:
        run(["git", "fetch", "--prune", "--tags", "origin"])
        tag = subprocess.check_output(
            ["git", "tag", "--sort=-version:refname", "--list", "v*"],
            cwd=WORKSPACE,
            text=True
        ).splitlines()[0]

        run(["git", "checkout", "--force", tag])
        run(["git", "reset", "--hard", tag])

        env = os.environ.copy()
        env["APP_VERSION"] = tag.removeprefix("v")
        env["GITHUB_OWNER"] = REPO.split("/", 1)[0]
        env["GITHUB_REPOSITORY"] = REPO.split("/", 1)[1]

        subprocess.run(
            ["docker", "compose", "up", "-d", "--build", "--remove-orphans", "app", "worker"],
            cwd=WORKSPACE,
            env=env,
            check=True
        )
        print("Update completed:", tag, flush=True)
    except Exception as exc:
        print("Update failed:", repr(exc), flush=True)
    finally:
        with lock:
            running = False

class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"OK")
            return
        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        if self.path != "/update":
            self.send_response(404)
            self.end_headers()
            return

        if TOKEN and self.headers.get("X-Updater-Token") != TOKEN:
            self.send_response(403)
            self.end_headers()
            return

        threading.Thread(target=update, daemon=True).start()
        self.send_response(202)
        self.end_headers()
        self.wfile.write(b"Update started")

HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
