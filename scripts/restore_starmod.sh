#!/bin/bash
# Restore StarMod.java from known good commit and apply Stage 1 patches
set -e
cd "$(dirname "$0")/.."
git show 2410b6a36b9b7bdc81fae01edaf4c84b5ea8c1e0:client/src/org/startegs/StarMod.java > client/src/org/startegs/StarMod.java
python3 - <<'PY'
from pathlib import Path
p = Path("client/src/org/startegs/StarMod.java")
t = p.read_text(encoding="utf-8")
if "MENU_ADMINS" not in t:
    t = t.replace(
        "public static final int MENU_HIDE = 9304;",
        "public static final int MENU_HIDE = 9304;\n    /** Пункт меню профиля: администраторы канала. */\n    public static final int MENU_ADMINS = 9305;",
        1,
    )
old = """            if (!filterEnabled() || m == null || m.messageOwner == null || m.messageOwner.out) return false;\n            if (m.type != MessageObject.TYPE_TEXT) return false;"""
new = """            if (m == null || m.messageOwner == null) return false;\n            if (StarAds.shouldHideMessage(m)) return true;\n            if (!filterEnabled() || m.messageOwner.out) return false;\n            if (m.type != MessageObject.TYPE_TEXT) return false;"""
if old in t:
    t = t.replace(old, new, 1)
p.write_text(t, encoding="utf-8")
print("StarMod restored + Stage1 patches applied")
PY
