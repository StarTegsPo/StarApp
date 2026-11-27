#!/usr/bin/env python3
"""Собирает HiveCorridors.rbxlx из исходников без Rojo.

Запуск: python3 tools/build_place.py  →  открыть HiveCorridors.rbxlx в Roblox Studio.
"""
import itertools
import pathlib
from xml.sax.saxutils import escape

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src"
OUT = ROOT / "HiveCorridors.rbxlx"
_ref = itertools.count()


def cdata(text: str) -> str:
    return "<![CDATA[" + text.replace("]]>", "]]]]><![CDATA[>") + "]]>"


def item(cls: str, name: str, source: str | None = None, children=()) -> str:
    props = f'<string name="Name">{escape(name)}</string>'
    if source is not None:
        props += f'<ProtectedString name="Source">{cdata(source)}</ProtectedString>'
    inner = "".join(children)
    return f'<Item class="{cls}" referent="RBX{next(_ref)}"><Properties>{props}</Properties>{inner}</Item>'


def read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


def modules(folder: pathlib.Path, skip: str):
    return [item("ModuleScript", p.stem, read(p)) for p in sorted(folder.glob("*.luau")) if p.name != skip]


def main():
    shared = item("Folder", "Shared", children=modules(SRC / "shared", skip=""))
    server = item(
        "Script", "HiveServer", read(SRC / "server" / "init.server.luau"),
        children=modules(SRC / "server", skip="init.server.luau"),
    )
    client = item("LocalScript", "HiveClient", read(SRC / "client" / "init.client.luau"))
    body = "".join([
        item("ReplicatedStorage", "ReplicatedStorage", children=[shared]),
        item("ServerScriptService", "ServerScriptService", children=[server]),
        item("StarterPlayer", "StarterPlayer", children=[
            item("StarterPlayerScripts", "StarterPlayerScripts", children=[client]),
        ]),
    ])
    xml = (
        '<roblox xmlns:xmime="http://www.w3.org/2005/05/xmlmime" '
        'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
        'xsi:noNamespaceSchemaLocation="http://www.roblox.com/roblox.xsd" version="4">'
        + body + "</roblox>\n"
    )
    OUT.write_text(xml, encoding="utf-8")
    print(f"OK: {OUT}")


if __name__ == "__main__":
    main()
