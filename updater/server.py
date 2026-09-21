"""Fixed-purpose FFH updater. Only a clean local main may fast-forward to public GitHub main."""
import json
import os
import re
import subprocess
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("UPDATER_TOKEN", "")
REPO = os.environ.get("REPOSITORY_DIR", "/opt/ffh_verwaltung")
GITHUB_REPOSITORY = os.environ.get("GITHUB_REPOSITORY", "kesselbuettner-dot/ffh_Verwaltung")
PORT = 8091
_lock = threading.Lock()
_state_lock = threading.Lock()
_state = {"status": "idle", "message": "Bereit", "target": None}


def run(args):
    """Run only caller-defined commands; never use shell or client-supplied arguments."""
    result = subprocess.run(args, cwd=REPO, text=True, capture_output=True, check=True)
    return result.stdout.strip()


def git(*args):
    return run(["git", "-c", "safe.directory=" + REPO, *args])


def set_state(status, message, target=None):
    with _state_lock:
        _state.update(status=status, message=message, target=target)


def set_env_commit(commit):
    path = os.path.join(REPO, ".env")
    lines = []
    if os.path.exists(path):
        with open(path, encoding="utf-8") as stream:
            lines = stream.read().splitlines()
    key = "APP_GIT_COMMIT="
    lines = [line for line in lines if not line.startswith(key)]
    lines.append(key + commit)
    temporary = path + ".updater.tmp"
    with open(temporary, "w", encoding="utf-8") as stream:
        stream.write("\n".join(lines) + "\n")
    if os.path.exists(path):
        os.chmod(temporary, os.stat(path).st_mode & 0o777)
    else:
        os.chmod(temporary, 0o600)
    os.replace(temporary, path)


def wait_for_app():
    for _ in range(30):
        try:
            run(["curl", "-fsS", "--max-time", "3", "http://app:8080/"])
            return
        except subprocess.CalledProcessError:
            time.sleep(2)
    raise RuntimeError("Anwendung antwortet nach dem Neustart nicht (HTTP-Startprüfung).")


def deploy():
    run(["docker", "compose", "up", "-d", "--build", "--remove-orphans",
         "app", "worker", "cloudflared"])
    wait_for_app()


def update():
    with _lock:
        previous = None
        changed = False
        try:
            if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", GITHUB_REPOSITORY):
                raise RuntimeError("Ungültige GitHub-Repository-Konfiguration.")
            if git("rev-parse", "--abbrev-ref", "HEAD") != "main":
                raise RuntimeError("Web-Update ist nur auf dem lokalen Branch main möglich; "
                                   "diese Version bitte wie bisher lokal installieren.")
            if git("status", "--porcelain", "--untracked-files=no"):
                raise RuntimeError("Lokale Änderungen vorhanden: Update abgebrochen. "
                                   "Bitte zuerst die Änderungen sichern.")
            previous = git("rev-parse", "HEAD")
            set_state("running", "GitHub main wird abgerufen")
            git("fetch", "--no-tags",
                "https://github.com/" + GITHUB_REPOSITORY + ".git", "main")
            latest = git("rev-parse", "FETCH_HEAD")
            if not re.fullmatch(r"[0-9a-fA-F]{40}", latest):
                raise RuntimeError("Ungültiger Commit von GitHub.")
            if latest == previous:
                set_state("success", "Installierter Commit ist bereits aktuell.", latest)
                return
            git("merge-base", "--is-ancestor", previous, latest)
            set_state("running", "Fast-forward und Docker-Build laufen", latest)
            git("merge", "--ff-only", latest)
            changed = True
            set_env_commit(latest)
            deploy()
            set_state("success", "App gestartet und HTTP-Startprüfung bestanden.", latest)
        except Exception as error:
            if changed and previous:
                try:
                    set_state("running", "Fehler beim Update; vorherigen App-Stand wiederherstellen", previous)
                    git("reset", "--hard", previous)
                    set_env_commit(previous)
                    deploy()
                    set_state("error", "Update fehlgeschlagen; vorheriger Code und App wieder gestartet. "
                                      "Datenbankmigrationen werden nicht automatisch zurückgerollt.", previous)
                except Exception:
                    set_state("error", "Update und automatischer Wiederanlauf fehlgeschlagen. "
                                      "Serverprotokoll prüfen; kein Datenbankrollback.", previous)
            else:
                set_state("error", "Update nicht ausgeführt: " + str(error)[:300], previous)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(fmt % args, flush=True)

    def reply(self, code, payload):
        raw = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def authorized(self):
        return bool(TOKEN) and self.headers.get("X-Updater-Token", "") == TOKEN

    def do_GET(self):
        if self.path != "/health":
            self.reply(404, {"error": "not found"})
            return
        with _state_lock:
            self.reply(200, _state.copy())

    def do_POST(self):
        if self.path != "/update":
            self.reply(404, {"error": "not found"})
            return
        if not self.authorized():
            self.reply(401, {"error": "unauthorized"})
            return
        with _state_lock:
            if _state["status"] == "running":
                self.reply(409, {"status": "running", "message": "Ein Update läuft bereits"})
                return
            _state.update(status="running", message="Update angefordert", target=None)
        threading.Thread(target=update, daemon=True).start()
        self.reply(202, {"status": "accepted", "message": "Update wurde gestartet"})


ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
