"""Fetches short authentic hadiths with their grade and explanation from HadeethEnc (hadeethenc.com),
so the app shows published text with its source, never text typed from memory."""
import json, os, time, urllib.request, urllib.parse

BASE = "https://hadeethenc.com/api/v1"
def get(path, **q):
    url = BASE + path + "?" + urllib.parse.urlencode(q)
    for i in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "yawmi-build"})
            return json.loads(urllib.request.urlopen(req, timeout=30).read().decode("utf-8"))
        except Exception as e:
            err = e; time.sleep(2 + 2 * i)
    print("FAILED", url, err); return None

roots = get("/categories/roots/", language="ar") or []
print("categories:", [(c.get("id"), c.get("title"), c.get("hadeeths_count")) for c in roots])
out, seen = [], set()
for c in roots:
    lst = get("/hadeeths/list/", language="ar", category_id=c["id"], page=1, per_page=60) or {}
    items = lst.get("data") or []
    kept = 0
    for it in items:
        if kept >= 9: break
        hid = it.get("id")
        if hid in seen: continue
        seen.add(hid)
        h = get("/hadeeths/one/", language="ar", id=hid)
        time.sleep(0.25)
        if not h: continue
        text, grade = (h.get("hadeeth") or "").strip(), (h.get("grade") or "").strip()
        if not text or len(text) > 280 or "صحيح" not in grade: continue
        if "ضعيف" in grade or "موضوع" in grade: continue
        out.append({"id": hid, "cat": c.get("title"), "text": text, "attribution": (h.get("attribution") or "").strip(),
                    "grade": grade, "explanation": (h.get("explanation") or "").strip(), "title": (h.get("title") or "").strip()})
        kept += 1
    print(c.get("title"), "->", kept)
os.makedirs("data", exist_ok=True)
json.dump(out, open("data/hadith.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("wrote", len(out), "hadiths")
if out: print(json.dumps(out[0], ensure_ascii=False)[:600])
