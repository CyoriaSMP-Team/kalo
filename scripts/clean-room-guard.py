#!/usr/bin/env python3
"""MIT-only provenance guard for Kalo artifacts.

Checks that what Kalo distributes is MIT and free of GPL-derived material that the
clean-room work identified:

1. `LICENSE` is MIT and keeps the upstream Neko notice.
2. No build file declares an RP Engine / resourcepack.ai dependency.
3. No distributed jar contains `ai/resourcepack`, `rpengine` or `resourcepack/ai` entries.
4. No distributed jar embeds a GPL/AGPL license text.
5. Plugin descriptors keep Kalo's own main classes.
6. Java sources carry no GPL/Affero license header or SPDX marker.

This is a regression guard, not a legal opinion. It runs without access to the
quarantined reference checkout, so CI and contributors can run it from a clean clone:

    python3 scripts/clean-room-guard.py

Exit code 1 means the tree would ship something the Full MIT policy forbids.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
import zipfile
from pathlib import Path

JAR_GLOBS = (
    "build/libs/Kalo-*.jar",
    "mohist/build/libs/Kalo-Mohist-*.jar",
    "compat/*/build/libs/Kalo-Compat-*.jar",
)
BUILD_FILES = ("build.gradle.kts", "settings.gradle.kts", "gradle.properties",
               "core/build.gradle.kts", "api/build.gradle.kts", "mohist/build.gradle.kts",
               "compat/nexo/build.gradle.kts", "buildSrc/build.gradle.kts")
SOURCE_ROOTS = ("core/src", "api/src", "mohist/src", "compat/nexo/src")
FORBIDDEN_ENTRY = re.compile(r"(^|/)(ai/resourcepack|rpengine)(/|$)", re.I)
GPL_MARKERS = (
    "GNU General Public License",
    "GNU Affero General Public License",
    "SPDX-License-Identifier: GPL",
    "SPDX-License-Identifier: AGPL",
)
FORBIDDEN_COORDINATES = ("resourcepack-ai", "rp-engine", "ai.resourcepack")


class Result:
    def __init__(self) -> None:
        self.failures: list[str] = []
        self.notes: list[str] = []

    def check(self, ok: bool, message: str) -> None:
        print(("PASS  " if ok else "FAIL  ") + message)
        if not ok:
            self.failures.append(message)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check_license(result: Result, root: Path) -> None:
    path = root / "LICENSE"
    if not path.is_file():
        result.check(False, "LICENSE is missing")
        return
    text = path.read_text(encoding="utf-8", errors="replace")
    result.check(text.lstrip().startswith("MIT License"), "LICENSE is MIT")
    result.check("Woobeen Jeon" in text, "LICENSE keeps the Neko upstream notice")
    result.check(not any(marker in text for marker in GPL_MARKERS),
                 "LICENSE contains no GPL text")


def check_build_files(result: Result, root: Path) -> None:
    offenders: list[str] = []
    for name in BUILD_FILES:
        path = root / name
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8", errors="replace").lower()
        for coordinate in FORBIDDEN_COORDINATES:
            if coordinate in text:
                offenders.append(f"{name}: {coordinate}")
    result.check(not offenders,
                 "no RP Engine dependency in build files"
                 + (f" ({', '.join(offenders)})" if offenders else ""))


def check_jar(result: Result, jar: Path) -> None:
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        bad_entries = [n for n in names if FORBIDDEN_ENTRY.search(n)]
        result.check(not bad_entries,
                     f"{jar.name}: no RP Engine packages"
                     + (f" ({len(bad_entries)} entries)" if bad_entries else ""))

        embedded_gpl: list[str] = []
        for name in names:
            if name.lower().endswith(("license", "license.txt", "copying", "notice")):
                try:
                    body = archive.read(name).decode("utf-8", "replace")
                except KeyError:
                    continue
                if any(marker in body for marker in GPL_MARKERS):
                    embedded_gpl.append(name)
        result.check(not embedded_gpl,
                     f"{jar.name}: no embedded GPL license"
                     + (f" ({', '.join(embedded_gpl)})" if embedded_gpl else ""))

        descriptors = [n for n in names if n.endswith("plugin.yml")]
        wrong_main: list[str] = []
        for name in descriptors:
            body = archive.read(name).decode("utf-8", "replace")
            for line in body.splitlines():
                if line.startswith("main:") and "io.kalo" not in line:
                    wrong_main.append(f"{name}: {line.strip()}")
        result.check(not wrong_main,
                     f"{jar.name}: plugin main class is io.kalo"
                     + (f" ({', '.join(wrong_main)})" if wrong_main else ""))

        result.notes.append(f"{jar}  sha256={sha256(jar)}  entries={len(names)}")


def check_sources(result: Result, root: Path) -> None:
    offenders: list[str] = []
    scanned = 0
    for source_root in SOURCE_ROOTS:
        directory = root / source_root
        if not directory.is_dir():
            continue
        for path in directory.rglob("*.java"):
            scanned += 1
            text = path.read_text(encoding="utf-8", errors="replace")
            for marker in GPL_MARKERS:
                if marker in text:
                    offenders.append(f"{path}: {marker}")
                    break
    result.check(not offenders,
                 f"no GPL header in {scanned} Java sources"
                 + (f" ({len(offenders)} files)" if offenders else ""))
    if offenders:
        for offender in offenders[:10]:
            print("      " + offender)


def discover_jars(root: Path) -> list[Path]:
    jars: list[Path] = []
    for pattern in JAR_GLOBS:
        jars.extend(sorted(root.glob(pattern)))
    return [jar for jar in jars if jar.is_file()]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--jar", type=Path, action="append", default=[])
    parser.add_argument("--skip-jars", action="store_true",
                        help="run the source/license checks before a build")
    args = parser.parse_args()

    root = args.root.resolve()
    result = Result()

    print("Kalo MIT-only provenance guard")
    print("=" * 60)
    check_license(result, root)
    check_build_files(result, root)
    check_sources(result, root)

    if not args.skip_jars:
        jars = [path.resolve() for path in args.jar] or discover_jars(root)
        result.check(bool(jars), f"found {len(jars)} jar(s) to inspect")
        for jar in jars:
            check_jar(result, jar)

    print("=" * 60)
    for note in result.notes:
        print(note)
    if result.failures:
        print(f"RESULT: FAIL ({len(result.failures)} check(s))")
        return 1
    print("RESULT: PASS — MIT-only distribution checks hold")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
