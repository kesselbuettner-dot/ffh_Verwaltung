"""Real HTTP regression for the appointment list; runs only against disposable GitHub CI containers."""
import datetime
import json
import re
import subprocess
import time
import urllib.error
import urllib.request

ROOT = "http://127.0.0.1:18080"


def api(path, token=None, method="GET", payload=None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(ROOT + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            if response.status != 200:
                raise AssertionError(f"{path}: HTTP {response.status}")
            return json.load(response)
    except urllib.error.HTTPError as error:
        raise AssertionError(f"{path}: HTTP {error.code}: {error.read().decode('utf-8')[:500]}") from error


def sql(statement):
    output = subprocess.check_output(
        ["docker", "exec", "fw-cockpit-cycle-db", "psql",
         "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1",
         "-U", "ci", "-d", "ci", "-c", statement], text=True
    )
    matches = re.findall(r"(?m)^\s*(\d+)\s*$", output)
    if not matches:
        raise AssertionError(f"No inserted ID returned: {output}")
    return int(matches[0])


# The startup migration must finish before the first authenticated request.
for attempt in range(30):
    try:
        token = api("/api/auth/login", method="POST",
                    payload={"username": "admin", "password": "admin123!"})["token"]
        break
    except (AssertionError, urllib.error.URLError):
        if attempt == 29:
            raise
        time.sleep(1)

assert api("/api/device-planning/sessions", token) == [], "Fresh database must have no appointments"

device_id = sql("""
INSERT INTO devices(name, category, location, active, inspection_required,
                    inspection_interval_months, next_inspection_date)
VALUES ('CI-Strahlrohr', 'Strahlrohr', 'CI-LF20', true, true, 6, CURRENT_DATE)
RETURNING id
""")
event_id = sql("""
INSERT INTO training_schedule_events(
    type, title, start_date, end_date, recurring, all_day, registration_required,
    active, audience_type, created_by, created_at, updated_at, device_inspection, device_locations,
    device_categories
)
VALUES ('SERVICE', 'CI-Geräteprüfung', CURRENT_DATE, CURRENT_DATE,
        false, true, false, true, 'ALL', 'ci', now(), now(), true, 'CI-LF20', 'Strahlrohr')
RETURNING id
""")
task_id = sql(f"""
INSERT INTO device_inspection_tasks(event_id, occurrence_date, device_id, status)
VALUES ({event_id}, CURRENT_DATE, {device_id}, 'PENDING')
RETURNING id
""")
today = datetime.date.today().isoformat()
entries = api("/api/device-planning/sessions", token)
assert len(entries) == 1, entries
appointment = entries[0]
assert appointment["eventId"] == event_id and appointment["date"] == today, appointment
assert appointment["total"] == 1 and appointment["completed"] == 0, appointment
assert appointment["reportId"] is None, appointment

detail = api(f"/api/device-planning/sessions/{event_id}/{today}", token)
assert len(detail["tasks"]) == 1 and detail["tasks"][0]["deviceId"] == device_id, detail
assert detail["tasks"][0]["id"] == task_id and detail["tasks"][0]["status"] == "PENDING", detail

api(f"/api/device-planning/tasks/{task_id}", token, "PUT",
    {"status": "INSPECTED", "note": "CI-Probe"})
entries = api("/api/device-planning/sessions", token)
assert len(entries) == 1 and entries[0]["completed"] == 1, entries
print("PASS: authenticated /api/device-planning/sessions handles empty, populated, and updated inspection appointments")
