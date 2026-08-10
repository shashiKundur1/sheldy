#!/bin/bash
# Generate Sheldi brand assets with Gemini 3 Pro Image (Nano Banana Pro).
# Usage: ./gen.sh <output-name> "<prompt>"
# Key comes from the environment so it never lands in this file.
set -e
: "${GEMINI_KEY:?set GEMINI_KEY first}"
OUT="$1"; PROMPT="$2"
DIR="$(cd "$(dirname "$0")" && pwd)"

python3 - "$OUT" "$PROMPT" "$DIR" <<'PY'
import json, os, sys, base64, urllib.request

out, prompt, d = sys.argv[1], sys.argv[2], sys.argv[3]
key = os.environ["GEMINI_KEY"]
url = f"https://generativelanguage.googleapis.com/v1beta/models/gemini-3-pro-image:generateContent?key={key}"

body = json.dumps({
    "contents": [{"parts": [{"text": prompt}]}],
    "generationConfig": {"responseModalities": ["IMAGE"]},
}).encode()

req = urllib.request.Request(url, data=body, headers={"Content-Type": "application/json"})
try:
    resp = json.load(urllib.request.urlopen(req, timeout=180))
except urllib.error.HTTPError as e:
    print("HTTP", e.code, e.read().decode()[:600]); sys.exit(1)

saved = 0
for cand in resp.get("candidates", []):
    for part in cand.get("content", {}).get("parts", []):
        blob = part.get("inlineData") or part.get("inline_data")
        if blob:
            path = os.path.join(d, f"{out}.png")
            open(path, "wb").write(base64.b64decode(blob["data"]))
            print("wrote", path, os.path.getsize(path), "bytes")
            saved += 1
if not saved:
    print("no image returned:", json.dumps(resp)[:600]); sys.exit(1)
PY
