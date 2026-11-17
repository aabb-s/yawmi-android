"""Fetches verse text and the Muyassar tafsir from published sources, so the app never ships text typed from memory.
Text: Tanzil simple edition; tafsir: King Fahd Complex "al-Tafsir al-Muyassar" (both via api.alquran.cloud).
The same verse is also read from quranenc.com as a cross-check and stored beside it."""
import json, sys, time, urllib.request

def get(url):
    for i in range(4):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "yawmi-build"})
            return json.loads(urllib.request.urlopen(req, timeout=30).read().decode("utf-8"))
        except Exception as e:
            err = e; time.sleep(2 + 2 * i)
    print("FAILED", url, err); return None

out = []
for line in open("tools/ayat-list.txt"):
    ref = line.strip()
    if not ref: continue
    s, a = ref.split(":")
    d = get(f"https://api.alquran.cloud/v1/ayah/{ref}/editions/quran-simple,ar.muyassar,quran-uthmani")
    if not d or d.get("code") != 200:
        print("skip", ref); sys.exit(1)
    by = {e["edition"]["identifier"]: e for e in d["data"]}
    q = get(f"https://quranenc.com/api/v1/translation/aya/arabic_moyassar/{s}/{a}") or {}
    r = q.get("result") or {}
    out.append({"ref": ref, "sura": by["quran-simple"]["surah"]["name"], "n": int(a),
                "text": by["quran-simple"]["text"], "uthmani": by["quran-uthmani"]["text"],
                "tafsir": by["ar.muyassar"]["text"],
                "check_text": r.get("arabic_text", ""), "check_tafsir": r.get("translation", "")})
    time.sleep(0.4)
json.dump(out, open("data/ayat.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("wrote", len(out), "verses")
