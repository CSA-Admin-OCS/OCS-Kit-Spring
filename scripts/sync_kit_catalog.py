#!/usr/bin/env python3
"""Re-sync src/main/resources/kits/catalog.json from the canonical OCS-Kit catalog.

Everything except the Spring-specific "spring" blocks is copied verbatim from the canonical
catalog (default ../OCS-Kit/kits/catalog.json, or pass a path). Each feature's existing
"spring" mapping (packages + paths) is kept; new features get an empty mapping for you to
fill in. Run `./mvnw test -Dtest=KitCatalogValidationTest` afterwards.
"""
import collections
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SPRING = os.path.join(HERE, "..", "src", "main", "resources", "kits", "catalog.json")
CANONICAL = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "..", "OCS-Kit", "kits", "catalog.json")

load = lambda p: json.load(open(p, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
canonical, current = load(CANONICAL), load(SPRING)

out = collections.OrderedDict()
for key, value in canonical.items():
    if key == "features":
        out["spring"] = current.get("spring", {})
        features = collections.OrderedDict()
        for fid, feature in value.items():
            merged = collections.OrderedDict(feature)
            old = current.get("features", {}).get(fid, {})
            merged["spring"] = old.get("spring", collections.OrderedDict([("packages", []), ("paths", [])]))
            if fid not in current.get("features", {}):
                print("new feature, fill in its spring mapping:", fid)
            features[fid] = merged
        for fid in current.get("features", {}):
            if fid not in value:
                print("feature removed upstream (its spring mapping is dropped):", fid)
        out["features"] = features
    else:
        out[key] = value

text = json.dumps(out, indent=2, ensure_ascii=False)
# Keep short string arrays on one line.
text = re.sub(r'\[\s*\n\s*((?:"[^"\n]*",?\s*\n\s*)*)\]',
              lambda m: "[" + ", ".join(x.strip().rstrip(",") for x in m.group(1).strip().split("\n") if x.strip()) + "]",
              text)
with open(SPRING, "w", encoding="utf-8") as f:
    f.write(text + "\n")
print("wrote", os.path.normpath(SPRING))
