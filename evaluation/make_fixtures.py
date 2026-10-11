"""Generate fixture folders from real branches of the demo repo (pitfalls §4).

Usage: python evaluation/make_fixtures.py [path-to-demo-repo]

A fixture is a folder: fixture.json + change.diff + files/<path> (contents at head).
Diffs and files always come from git, never from hand-editing.
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

DEMO = Path(sys.argv[1] if len(sys.argv) > 1 else "E:/production/order-service-demo")
OUT = Path(__file__).parent / "datasets"
CTRL = "src/main/java/io/github/rahu09/orderservice/order/OrderController.java"
SERVICE = "src/main/java/io/github/rahu09/orderservice/order/OrderService.java"

FIXTURES = [
    {
        "id": "list-orders-unpaginated",
        "family": "pagination",
        "kind": "buggy",
        "branch": "feature/list-orders-unpaginated",
        "expectedFindings": [{
            "file": CTRL, "line": 32, "category": "GUIDELINE", "ruleId": "API-03",
            "why": "GET /orders returns every order in one response; the collection endpoint is not paginated.",
        }],
    },
    {
        "id": "list-orders-paginated",
        "family": "pagination",
        "kind": "clean",
        "branch": "clean/list-orders-paginated",
        "expectedFindings": [],
    },
    {
        "id": "cancel-order-swallowed",
        "family": "exceptions",
        "kind": "buggy",
        "branch": "feature/cancel-order-swallowed",
        "expectedFindings": [{
            "file": SERVICE, "line": 28, "category": "CORRECTNESS", "ruleId": "ERR-02",
            "why": "OrderStateException is caught and swallowed, so cancelling a shipped order returns 200 with the unchanged order.",
        }],
    },
    {
        "id": "cancel-order-handled",
        "family": "exceptions",
        "kind": "clean",
        "branch": "clean/cancel-order-handled",
        "expectedFindings": [],
    },
]


def git(*args: str) -> str:
    return subprocess.run(["git", "-C", str(DEMO), *args], check=True, capture_output=True, text=True,
                          encoding="utf-8").stdout


for f in FIXTURES:
    folder = OUT / "dev" / f["id"]
    if folder.exists():
        shutil.rmtree(folder)
    (folder / "files").mkdir(parents=True)
    diff = git("diff", "--no-color", f"main...{f['branch']}")
    (folder / "change.diff").write_text(diff, encoding="utf-8", newline="\n")
    changed = git("diff", "--name-only", f"main...{f['branch']}").split()
    for path in changed:
        target = folder / "files" / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(git("show", f"{f['branch']}:{path}"), encoding="utf-8", newline="\n")
    meta = {
        "id": f["id"], "family": f["family"], "kind": f["kind"],
        "sourceBranch": f["branch"], "baseBranch": "main",
        "headSha": git("rev-parse", f["branch"]).strip(),
        "baseSha": git("rev-parse", "main").strip(),
        "expectedFindings": f["expectedFindings"],
        "expectedRuleIds": sorted({e["ruleId"] for e in f["expectedFindings"]}),
    }
    (folder / "fixture.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f["id"], f["kind"], len(changed), "files")

guidelines = OUT / "guidelines"
if guidelines.exists():
    shutil.rmtree(guidelines)
guidelines.mkdir(parents=True)
shutil.copy(DEMO / "docs" / "engineering-guidelines.md", guidelines / "engineering-guidelines.md")
