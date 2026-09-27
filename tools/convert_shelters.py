"""Convert the trimmed shelter GeoJSON into the compact JSON bundled with the app.

Source: shelters.geojson from the Skyddsrum web project, which is built from the
public INSPIRE WFS of Myndigheten för civilt försvar (formerly MSB),
layer skyddsrum:US_civilProtectionSite, enriched with municipality names.

Usage: python tools/convert_shelters.py path/to/shelters.geojson
Output: app/src/main/assets/shelters.json
"""
import json
import sys
from pathlib import Path

src = Path(sys.argv[1] if len(sys.argv) > 1 else "../shelters.geojson")
out = Path(__file__).resolve().parent.parent / "app/src/main/assets/shelters.json"

features = json.loads(src.read_text(encoding="utf-8"))["features"]
shelters = []
for f in features:
    coords = f["geometry"]["coordinates"]
    lon, lat = coords[0] if isinstance(coords[0], list) else coords  # MultiPoint with one point, or Point
    p = f["properties"]
    shelters.append({
        "id": p["id"],
        "address": (p.get("address") or "").strip(", "),
        "municipality": p.get("municipality") or "",
        "lat": round(lat, 6),
        "lon": round(lon, 6),
        "capacity": int(p.get("capacity") or 0),
    })

out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(shelters, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"Wrote {len(shelters)} shelters to {out} ({out.stat().st_size / 1e6:.1f} MB)")
