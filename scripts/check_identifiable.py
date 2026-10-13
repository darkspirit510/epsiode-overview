#!/usr/bin/env python3
"""Blocks identifiable strings (series names, tokens, usernames, local paths) from being committed.

The denylist is built from local, gitignored data only, so it never leaves this machine:
series names from cache/, the ignored conf files, the folders below baseDir/subDirs,
the TMDB token, the OS username and identifiable.local.txt (one term per line).
Without that data (e.g. in CI) only the secret and path patterns are checked.

Generic words that are also series names go into config/identifiable-allowlist.txt.

Usage: check_identifiable.py --staged   scan files staged for commit (pre-commit hook)
       check_identifiable.py --all      scan tracked and untracked, non-ignored files
"""
import getpass
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESOURCES = ROOT / "src/main/resources"
ALLOWLIST = ROOT / "config/identifiable-allowlist.txt"
LOCAL_TERMS = ROOT / "identifiable.local.txt"
MIN_TERM_LENGTH = 3
SEASON_OR_NUMBER = re.compile(r"[se]?\d+")

SECRET_PATTERNS = [
    ("hex key", re.compile(r"\b[0-9a-fA-F]{32}\b")),
    ("JWT", re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}")),
    ("credential literal", re.compile(
        r"(?i)(token|api_?key|secret|password)\w*\s*[=:]\s*[\"']?(?!your_)[A-Za-z0-9_\-]{16,}")),
    ("local path", re.compile(r"/(Users|home|Volumes|mnt)/[A-Za-z0-9._-]+")),
]


def normalize(text):
    return " " + re.sub(r"[^0-9a-z]+", " ", text.lower()).strip() + " "


def conf_value(text, key):
    match = re.search(rf"^\s*{key}\s*=\s*(.+)$", text, re.MULTILINE)
    return match.group(1).strip() if match else None


def read_text(path):
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return ""


def series_from_cache():
    for path in (ROOT / "cache/tmdb").glob("*.json"):
        try:
            yield json.loads(read_text(path)).get("name", "")
        except (ValueError, AttributeError):
            pass
    try:
        for key in json.loads(read_text(ROOT / "cache/episodes/all.json")):
            yield key.split("/", 1)[-1]
    except (ValueError, TypeError):
        pass
    try:
        for row in json.loads(read_text(ROOT / "cache/table_data.json")):
            if isinstance(row, dict) and "seriesName" in row:
                yield re.sub(r"\s*\(Vollständig\)$", "", row["seriesName"])
    except (ValueError, TypeError):
        pass


def series_from_confs():
    for line in read_text(RESOURCES / "fuzz_episodes.conf").splitlines():
        if line.strip() and not line.startswith("#"):
            yield line.strip()
    for line in read_text(RESOURCES / "overwrite.conf").splitlines():
        if ":" in line and not line.startswith("#"):
            yield line.rsplit(":", 1)[0]
    blacklist = "\n".join(line for line in read_text(RESOURCES / "blacklist.conf").splitlines()
                          if not line.lstrip().startswith("#"))
    yield from re.findall(r"\"([^\"]+)\"", blacklist)


def application_conf_terms():
    conf = read_text(RESOURCES / "application.conf")
    token = conf_value(conf, "tmdbApiToken")
    if token:
        yield token.strip("\"")
    base_dir = conf_value(conf, "baseDir")
    sub_dirs = conf_value(conf, "subDirs")
    if not base_dir:
        return
    base = Path(base_dir.strip("\""))
    yield str(base)
    for sub_dir in re.findall(r"\"([^\"]+)\"", sub_dirs or ""):
        try:
            for child in (base / sub_dir).iterdir():
                if child.is_dir():
                    yield child.name
        except OSError:
            pass


def denylist():
    allowed = {normalize(line) for line in read_text(ALLOWLIST).splitlines()
               if line.strip() and not line.startswith("#")}
    terms = [*series_from_cache(), *series_from_confs(), *application_conf_terms(), getpass.getuser(),
             *(line for line in read_text(LOCAL_TERMS).splitlines() if not line.startswith("#"))]
    result = {}
    for term in terms:
        key = normalize(term)
        if len(key.strip()) >= MIN_TERM_LENGTH and not SEASON_OR_NUMBER.fullmatch(key.strip()) and key not in allowed:
            result[key] = term.strip()
    return result


def git(*args):
    return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, check=True).stdout


def files_to_scan(staged):
    if staged:
        names = git("diff", "--cached", "--name-only", "--diff-filter=ACMR", "-z").decode().split("\0")
        return [(name, git("show", f":{name}")) for name in names if name]
    names = git("ls-files", "-z", "--cached", "--others", "--exclude-standard").decode().split("\0")
    return [(name, (ROOT / name).read_bytes()) for name in names if name and (ROOT / name).is_file()]


def main():
    if sys.argv[1:] not in (["--staged"], ["--all"]):
        print(__doc__)
        return 2
    terms = denylist()
    skipped = {str(ALLOWLIST.relative_to(ROOT)), str(Path(__file__).relative_to(ROOT))}
    findings = []
    for name, content in files_to_scan(sys.argv[1] == "--staged"):
        if name in skipped or b"\0" in content:
            continue
        lines = [f"<path> {name}", *content.decode("utf-8", errors="replace").splitlines()]
        for number, line in enumerate(lines):
            normalized = normalize(line)
            for key, term in terms.items():
                if key in normalized:
                    findings.append(f"{name}:{number}: identifiable term '{term}'")
            for label, pattern in SECRET_PATTERNS:
                if pattern.search(line):
                    findings.append(f"{name}:{number}: {label}: {line.strip()[:120]}")
    if findings:
        print("Identifiable strings found (line 0 = file path):", *findings, sep="\n  ")
        print("Replace them with dummy values. If a match is a generic word, "
              "add it to config/identifiable-allowlist.txt.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
