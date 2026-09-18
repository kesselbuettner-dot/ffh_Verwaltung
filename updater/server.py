import os
import subprocess
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

TOKEN = os.environ.get("UPDATER_TOKEN", "")
WORKSPACE = os.environ.get("UPDATER_WORKSPACE", "/workspace")
PORT = int(os.environ.get("UPDATER_PORT", "8090"))
REPO = os.environ.get("GITHUB_REPOSITORY", "kesselbuettner-dot/ffh_Verwaltung")
ALLOWED_METHOD = "POST"
running = False
lock = threading.Lock()

def run_git(args):
    subprocess.run(["git", *args], cwd=WORKSPACE, check=True, timeout=180)

def update():
    global running
    with lock:
        if running:
            return
        running = True
    try:
        # Only fetch tags from the configured repository.
        run_git(["fetch", "--prune", "--tags", "origin"])
        tags = subprocess.check_output(
            ["git", "tag", "--sort=-version:refname", "--list", "v*.*.*"],
            cwd=WORKSPACE, text=True, timeout=30
        ).splitlines()
        if not tags:
            raise RuntimeError("Kein SemVer-Release-Tag gefunden.")
        tag = tags[0]

        # Only checked-out release tags are accepted.
        run_git(["checkout", "--force", tag])
        run_git(["reset", "--hard", tag])

        version = tag[1:]
        if not version.replace(".", "").isdigit() or version.count(".") != 2:
            raise RuntimeError("Ungueltiger Release-Tag.")

        owner, repo = REPO.split("/", 1)
        env = {
            "PATH": "/usr/local/bin:/usr/bin:/bin",
            "HOME": "/tmp",
            "APP_VERSION": version,
            "GITHUB_OWNER": owner,
            "GITHUB_REPOSITORY": repo,
        }

        # Deliberately allow exactly one Docker operation:
        # build/start only the application services; never arbitrary user input.
        subprocess.run(
            ["docker", "compose", "up", "-d", "--build", "--remove-orphans", "app", "worker"],
            cwd=WORKSPACE, env=env, check=True, timeout=900
        )
        print("Update completed:", tag, flush=True)
    except Exception as exc:
        print("Update failed:", type(exc).__name__, flush=True)
    finally:
        with lock:
            running = False

class Handler(BaseHTTPRequestHandler):
    server_version = "FFH-Updater/1.0"

    def log_message(self, fmt, *args):
        print("HTTP:", fmt % args, flush=True)

    def _send(self, code, body=b""):
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        if body:
            self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self._send(200, b"OK")
        else:
            self._send(404)

    def do_POST(self):
        if urlparse(self.path).path != "/update":
            self._send(404)
            return
        if self.headers.get("X-Updater-Token") != TOKEN or not TOKEN:
            self._send(403)
            return

        threading.Thread(target=update, daemon=True).start()
        self._send(202, b"Update started")

    def do_PUT(self):
        self._send(405)

    def do_DELETE(self):
        self._send(405)

    def do_PATCH(self):
        self._send(405)

    def do_OPTIONS(self):
        self._send(405)

ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
