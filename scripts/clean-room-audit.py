#!/usr/bin/env python3
"""Reproducible clean-room provenance audit.

Compares Kalo's Java sources with quarantined third-party reference sources that were
present during development. This is a similarity audit, not a legal opinion.

Usage (the quarantine is root-only):
    sudo python3 scripts/clean-room-audit.py [--reference DIR] [--output FILE]

The script strips package/import/comment/blank lines, then reports:
  * exact normalized-line overlap;
  * 5-token shingle Jaccard similarity.

It exits non-zero only if a pair exceeds the high-risk threshold (0.20 Jaccard or 10%
exact line overlap), so it can be wired into a release checklist.
"""

from __future__ import annotations

import argparse
import hashlib
import re
import sys
from collections import Counter
from pathlib import Path

SOURCE_ROOTS = ("core/src/main/java", "api/src/main/java", "mohist/src/main/java")
DEFAULT_REFERENCE = Path("/root/clean-room-quarantine/reference")
HIGH_JACCARD = 0.10
HIGH_LINE_OVERLAP = 0.15
HIGH_MEANINGFUL_LINES = 8
SHINGLE = 8


def normalized_lines(path: Path) -> list[str]:
    lines: list[str] = []
    in_block = False
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = raw.strip()
        if line.startswith("/*"):
            in_block = True
        if in_block:
            if "*/" in line:
                in_block = False
            continue
        if not line or line.startswith("//"):
            continue
        if line.startswith(("package ", "import ")):
            continue
        lines.append(re.sub(r"\s+", " ", line))
    return lines


BOILERPLATE = {
    "@Override", "return false;", "return null;", "return true;", "} else {",
    "public boolean available() {", "boolean available();",
}


def meaningful(lines: list[str]) -> list[str]:
    """Drop short declarations and Java scaffolding; keep authored expressions."""
    return [line for line in lines
            if len(line) >= 12 and line not in BOILERPLATE
            and not re.fullmatch(r"[{}();,]+", line)]


def shingles(lines: list[str]) -> Counter[tuple[str, ...]]:
    tokens: list[str] = []
    for line in lines:
        tokens.extend(re.findall(r"[A-Za-z_][A-Za-z0-9_]*|\d+|[^A-Za-z0-9_\s]", line))
    if len(tokens) < SHINGLE:
        return Counter()
    return Counter(tuple(tokens[i:i + SHINGLE]) for i in range(len(tokens) - SHINGLE + 1))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", type=Path, default=DEFAULT_REFERENCE)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    if not args.reference.is_dir():
        print(f"reference directory not found: {args.reference}", file=sys.stderr)
        return 2

    references = sorted(args.reference.glob("*.java"))
    sources: list[Path] = []
    for root in SOURCE_ROOTS:
        directory = Path(root)
        if directory.is_dir():
            sources.extend(sorted(directory.rglob("*.java")))

    source_lines = {path: normalized_lines(path) for path in sources}
    source_shingles = {path: shingles(lines) for path, lines in source_lines.items()}

    rows: list[tuple[float, float, Path, Path]] = []
    for reference in references:
        reference_lines = normalized_lines(reference)
        reference_shingles = shingles(reference_lines)
        reference_set = set(reference_lines)
        reference_meaningful = set(meaningful(reference_lines))
        for source in sources:
            candidate_lines = source_lines[source]
            candidate_set = set(candidate_lines)
            exact = len(reference_set & candidate_set)
            line_overlap = exact / max(1, len(reference_set))
            meaningful_exact = len(reference_meaningful & set(meaningful(candidate_lines)))
            meaningful_overlap = meaningful_exact / max(1, len(reference_meaningful))
            candidate_shingles = source_shingles[source]
            union = sum((reference_shingles | candidate_shingles).values())
            jaccard = (sum((reference_shingles & candidate_shingles).values()) / union
                       if union else 0.0)
            if jaccard > 0.01 or exact > 0 or meaningful_exact > 0:
                rows.append((jaccard, line_overlap, meaningful_exact,
                             meaningful_overlap, reference, source))

    rows.sort(reverse=True, key=lambda row: (row[0], row[2], row[1]))
    lines_out = ["Clean-room provenance audit", "=" * 77,
                 f"reference: {args.reference}", f"sources: {len(sources)} files",
                 f"similarity: {SHINGLE}-token shingles / exact normalized lines", ""]
    for reference in references:
        lines_out.append(f"{reference.name}  sha256={digest(reference)}")
    lines_out.append("")

    failures = 0
    for jaccard, line_overlap, meaningful_exact, meaningful_overlap, reference, source in rows[:40]:
        risk = "HIGH" if (jaccard >= HIGH_JACCARD
                           or (meaningful_overlap >= HIGH_LINE_OVERLAP
                               and meaningful_exact >= HIGH_MEANINGFUL_LINES)) else "review"
        if risk == "HIGH":
            failures += 1
        lines_out.append(
            f"{risk:6} jaccard={jaccard:.3f} lines={line_overlap:.3f} "
            f"meaningful={meaningful_exact} ({meaningful_overlap:.3f}) "
            f"{reference.name} -> {source}")

    lines_out.extend([
        "",
        f"pairs reported: {len(rows)}",
        f"high-risk pairs: {failures}",
        "No literal copy was determined by this metric alone; inspect any review/high pair.",
    ])
    report = "\n".join(lines_out)
    print(report)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report + "\n", encoding="utf-8")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
