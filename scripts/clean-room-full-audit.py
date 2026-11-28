#!/usr/bin/env python3
"""Full-tree clean-room similarity audit.

Compares Kalo's Java main sources, Java test sources, and text resources with a full
RP Engine checkout. Code and comment shingles are scored separately; resource text gets
its own section. Reference source text is never printed — only file names and scores.

Run as root because the reference checkout is root-only:

    sudo python3 scripts/clean-room-full-audit.py \
        --reference /root/clean-room-quarantine/rp-engine \
        --output /tmp/clean-room-full-audit.txt

Exit code is 1 when any pair reaches the review threshold (0.15), so the script can be
used as a release gate when a reference checkout is available. A score is a review
pointer, not a legal conclusion.
"""

from __future__ import annotations

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

MAIN_ROOTS = ("core/src/main/java", "api/src/main/java", "mohist/src/main/java")
TEST_ROOTS = ("core/src/test/java", "api/src/test/java", "mohist/src/test/java")
RESOURCE_ROOTS = (
    "core/src/main/resources",
    "core/src/test/resources",
    "api/src/main/resources",
    "api/src/test/resources",
    "mohist/src/main/resources",
    "mohist/src/test/resources",
    "assets",
)
TEXT_SUFFIXES = {
    ".json", ".yml", ".yaml", ".mcmeta", ".lang", ".properties", ".txt",
    ".glsl", ".vsh", ".fsh", ".mjs", ".cjs", ".js", ".md",
}
SHINGLE = 8
COMMENT_SHINGLE = 5
RESOURCE_SHINGLE = 8
THRESHOLD = 0.15
TOP = 40


def code_lines(path: Path) -> list[str]:
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


def comment_text(path: Path) -> str:
    text = path.read_text(encoding="utf-8", errors="replace")
    out: list[str] = []
    for match in re.finditer(r"/\*.*?\*/|//[^\n]*", text, re.S):
        body = match.group(0)
        body = re.sub(r"^/\*+|\*+/$", "", body)
        body = re.sub(r"^\s*//", "", body, flags=re.M)
        body = re.sub(r"^\s*\*", "", body, flags=re.M)
        out.append(body)
    return " ".join(out)


def tokens(text: str) -> list[str]:
    return re.findall(r"[A-Za-z_][A-Za-z0-9_]*|\d+|[^A-Za-z0-9_\s]", text)


def shingle_set(text: str, n: int) -> set[tuple[str, ...]]:
    words = tokens(text)
    if len(words) < n:
        return set()
    return {tuple(words[i:i + n]) for i in range(len(words) - n + 1)}


def build_index(source_sets: list[set[tuple[str, ...]]]) -> dict[tuple[str, ...], list[int]]:
    index: dict[tuple[str, ...], list[int]] = defaultdict(list)
    for idx, shingles in enumerate(source_sets):
        for shingle in shingles:
            index[shingle].append(idx)
    return index


def rank(reference_sets: list[set[tuple[str, ...]]],
         source_sets: list[set[tuple[str, ...]]]) -> list[tuple[float, int, int]]:
    index = build_index(source_sets)
    rows: list[tuple[float, int, int]] = []
    for r_idx, ref in enumerate(reference_sets):
        if not ref:
            continue
        counts: dict[int, int] = defaultdict(int)
        for shingle in ref:
            for s_idx in index.get(shingle, ()):
                counts[s_idx] += 1
        for s_idx, overlap in counts.items():
            union = len(ref) + len(source_sets[s_idx]) - overlap
            if union <= 0:
                continue
            jaccard = overlap / union
            if jaccard >= 0.01:
                rows.append((jaccard, r_idx, s_idx))
    rows.sort(reverse=True)
    return rows[:TOP]


def gather_java(roots: tuple[str, ...]) -> list[Path]:
    files: list[Path] = []
    for root in roots:
        directory = Path(root)
        if directory.is_dir():
            files.extend(sorted(directory.rglob("*.java")))
    return files


def gather_text(roots: tuple[str, ...]) -> list[Path]:
    files: list[Path] = []
    for root in roots:
        directory = Path(root)
        if not directory.is_dir():
            continue
        for path in sorted(directory.rglob("*")):
            if path.is_file() and path.suffix.lower() in TEXT_SUFFIXES:
                files.append(path)
    return files


def section(lines: list[str], title: str, rows: list[tuple[float, int, int]],
            references: list[Path], sources: list[Path], label: str) -> int:
    high = sum(1 for score, _, _ in rows if score >= THRESHOLD)
    lines.append(title)
    for score, r_idx, s_idx in rows[:20]:
        marker = "REVIEW" if score >= THRESHOLD else "      "
        lines.append(f"  {marker} {score:.3f}  {references[r_idx].name} -> {sources[s_idx]}")
    if not rows:
        lines.append("  (no pair at or above 0.01)")
    lines.append("")
    lines.append(f"{label} pairs >= {THRESHOLD}: {high}")
    lines.append("")
    return high


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--skip-tests", action="store_true")
    parser.add_argument("--skip-resources", action="store_true")
    args = parser.parse_args()

    references = sorted(args.reference.rglob("*.java"))
    main_sources = gather_java(MAIN_ROOTS)
    test_sources = [] if args.skip_tests else gather_java(TEST_ROOTS)
    if not references or not main_sources:
        print("reference or source files missing", file=sys.stderr)
        return 2

    ref_code = [shingle_set("\n".join(code_lines(p)), SHINGLE) for p in references]
    ref_comments = [shingle_set(comment_text(p), COMMENT_SHINGLE) for p in references]

    def score_java(paths: list[Path]) -> tuple[int, int]:
        src_code = [shingle_set("\n".join(code_lines(p)), SHINGLE) for p in paths]
        src_comments = [shingle_set(comment_text(p), COMMENT_SHINGLE) for p in paths]
        code_rows = rank(ref_code, src_code)
        comment_rows = rank(ref_comments, src_comments)
        return code_rows, comment_rows

    lines = ["Full-tree clean-room similarity audit", "=" * 88,
             f"reference: {args.reference}",
             f"references: {len(references)} Java files",
             f"main sources: {len(main_sources)}  test sources: {len(test_sources)}",
             f"code shingles: {SHINGLE}-token  comment shingles: {COMMENT_SHINGLE}-token",
             "scores are Jaccard; reference source text is never printed", ""]

    high = 0
    main_code, main_comments = score_java(main_sources)
    high += section(lines, "Main code", main_code, references, main_sources, "main code")
    high += section(lines, "Main comments", main_comments, references, main_sources,
                     "main comment")

    if test_sources:
        test_code, test_comments = score_java(test_sources)
        high += section(lines, "Test code", test_code, references, test_sources, "test code")
        high += section(lines, "Test comments", test_comments, references, test_sources,
                         "test comment")

    if not args.skip_resources:
        reference_text = [p for p in args.reference.rglob("*")
                          if p.is_file() and p.suffix.lower() in TEXT_SUFFIXES]
        source_text = gather_text(RESOURCE_ROOTS)
        if reference_text and source_text:
            ref_res = [shingle_set(p.read_text(encoding="utf-8", errors="replace"),
                                   RESOURCE_SHINGLE) for p in reference_text]
            src_res = [shingle_set(p.read_text(encoding="utf-8", errors="replace"),
                                   RESOURCE_SHINGLE) for p in source_text]
            resource_rows = rank(ref_res, src_res)
            high += section(lines, "Resources and assets", resource_rows,
                            reference_text, source_text, "resource")
            lines.append(f"reference text files: {len(reference_text)}  "
                         f"Kalo text files: {len(source_text)}")
            lines.append("")

    lines.append(f"total pairs >= {THRESHOLD}: {high}")
    lines.append("A score is a review pointer, not a legal conclusion.")

    report = "\n".join(lines)
    print(report)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(report + "\n", encoding="utf-8")
    return 1 if high else 0


if __name__ == "__main__":
    raise SystemExit(main())
