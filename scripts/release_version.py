"""Select an immutable semantic release version, ignoring legacy preview tags."""
import re
import subprocess
from pathlib import Path

PATTERN = re.compile(r"v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)")

def parse(value):
    match = PATTERN.fullmatch(value.strip())
    if not match:
        return None
    parts = tuple(map(int, match.groups()))
    if parts[1] >= 1000 or parts[2] >= 1000 or parts[0] * 1_000_000 + parts[1] * 1000 + parts[2] > 2_100_000_000:
        raise ValueError("Release version exceeds Android versionCode bounds")
    return parts

def choose_version(base, tags, head_tags=()):
    minimum = parse(base)
    if minimum is None:
        raise ValueError("Invalid appVersion")
    published = [parse(t[1:]) for t in tags if t.startswith("v") and PATTERN.fullmatch(t)]
    published = [v for v in published if v is not None]
    same_commit = [parse(t[1:]) for t in head_tags if t.startswith("v") and PATTERN.fullmatch(t)]
    reusable = [v for v in same_commit if v is not None and v >= minimum]
    if reusable:
        chosen = max(reusable)
    elif not published or max(published) < minimum:
        chosen = minimum
    else:
        major, minor, patch = max(published)
        patch += 1
        if patch >= 1000:
            minor += 1
            patch = 0
        if minor >= 1000:
            major += 1
            minor = 0
        chosen = (major, minor, patch)
    name = ".".join(map(str, chosen))
    parse(name)
    code = chosen[0] * 1_000_000 + chosen[1] * 1000 + chosen[2]
    return name, code

if __name__ == "__main__":
    properties = Path("gradle.properties").read_text(encoding="utf-8")
    base = re.search(r"^appVersion=(.+)$", properties, re.MULTILINE).group(1).strip()
    tags = subprocess.check_output(["git", "tag", "--list"], text=True).splitlines()
    head = subprocess.check_output(["git", "tag", "--points-at", "HEAD"], text=True).splitlines()
    name, code = choose_version(base, tags, head)
    print(f"version={name}\ntag=v{name}\nversion_code={code}")