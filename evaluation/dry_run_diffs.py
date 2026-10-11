"""Manual dry run (Chunk 3): send each dev fixture's change.diff to POST /api/dry-run/diff and print the findings.

Usage: python evaluation/dry_run_diffs.py [fixture-id ...]
Needs the app running on localhost:8080 with a real LLM key. Throttled: free tiers rate-limit (pitfalls §3).
"""
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

DEV = Path(__file__).parent / "datasets" / "dev"
ids = sys.argv[1:] or sorted(p.name for p in DEV.iterdir() if p.is_dir())

for fid in ids:
    folder = DEV / fid
    meta = json.loads((folder / "fixture.json").read_text(encoding="utf-8"))
    diff = (folder / "change.diff").read_text(encoding="utf-8")
    req = urllib.request.Request(
        "http://localhost:8080/api/dry-run/diff",
        data=json.dumps({"diff": diff}).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            body = json.loads(resp.read())
    except urllib.error.HTTPError as e:
        print(f"\n=== {fid} ({meta['kind']}) HTTP {e.code}: {e.read().decode()[:300]}")
        continue
    print(f"\n=== {fid} ({meta['kind']}) {body['provider']}/{body['model']} "
          f"{body['inputTokens']} in / {body['outputTokens']} out, {time.time() - started:.1f}s")
    expected = [(e["file"].rsplit("/", 1)[-1], e["line"], e["ruleId"]) for e in meta["expectedFindings"]]
    print("expected:", expected or "none")
    for f in body["findings"]:
        print(f"  - {f['filePath'].rsplit('/', 1)[-1]}:{f['line']} [{f['category']}] {f['title']}")
    if not body["findings"]:
        print("  (no findings)")
    time.sleep(4)
