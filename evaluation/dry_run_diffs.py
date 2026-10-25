"""Manual dry run: send each dev fixture through POST /api/dry-run/fixture/{id} and print what came back.

Usage: python evaluation/dry_run_diffs.py [fixture-id ...]
Needs the app running on localhost:8080 with a real LLM key. Throttled: free tiers rate-limit (pitfalls §3).

Since Chunk 4 the app maps the model's location IDs to path + line itself, so "expected" and "got" can be
compared directly: the line shown is the one GitHub would receive.
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
    req = urllib.request.Request("http://localhost:8080/api/dry-run/fixture/" + fid, data=b"{}", headers={"Content-Type": "application/json"}, method="POST")
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            body = json.loads(resp.read())
    except urllib.error.HTTPError as e:
        print(f"\n=== {fid} HTTP {e.code}: {e.read().decode()[:300]}")
        continue
    exp = body["expected"]
    print(f"\n=== {fid} ({exp['kind']}) {body['provider']}/{body['model']} {body['promptVersion']} "
          f"{body['inputTokens']} in / {body['outputTokens']} out, {time.time() - started:.1f}s, "
          f"{body['allowedLocations']} allowed locations, {len(body['filesReviewed'])} files reviewed, "
          f"{len(body['filesSkipped'])} skipped")
    print("expected:", [(e["file"].rsplit("/", 1)[-1], e["line"], e["ruleId"]) for e in exp["expectedFindings"]] or "none")
    for f in body["findings"]:
        where = f"{f['path'].rsplit('/', 1)[-1]}:{f['line']}" if f["path"] else f"({f['locationId'] or 'no location'})"
        reason = f" -- {f['reason']}" if f["reason"] else ""
        print(f"  - {f['status']:<12} {where} [{f['category']}] {f['title']}{reason}")
    if not body["findings"]:
        print("  (no findings)")
    time.sleep(4)
