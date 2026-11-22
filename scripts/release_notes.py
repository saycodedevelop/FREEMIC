"""Render release notes from explicitly translated Unreleased changelog sections."""
import argparse
import re
import sys
from pathlib import Path
from release_version import parse

ROOT = Path(__file__).resolve().parents[1]
LANGUAGES = {"th": "ไทย", "en": "English", "zh-CN": "简体中文", "vi": "Tiếng Việt", "id": "Bahasa Indonesia"}


def read_changes(changelog):
    section = re.search(r"^## \[Unreleased\]\s*\n(.*?)(?=^## \[|\Z)", changelog, re.M | re.S)
    if not section:
        raise ValueError("CHANGELOG.md must contain an Unreleased section")
    changes = {}
    pattern = r"^### \[([^\]]+)\][^\n]*\n(.*?)(?=^### \[|\Z)"
    for match in re.finditer(pattern, section.group(1), re.M | re.S):
        language, body = match.group(1), match.group(2).strip()
        if language not in LANGUAGES or language in changes or not body:
            raise ValueError(f"Invalid, duplicate, or empty changelog translation: {language}")
        changes[language] = body
    missing = set(LANGUAGES) - changes.keys()
    if missing:
        raise ValueError(f"Missing changelog translations: {', '.join(sorted(missing))}")
    return changes


def render_release(version, root=ROOT, language=None):
    if parse(version) is None:
        raise ValueError("Expected a semantic version such as 0.4.0")
    if language is not None and language not in LANGUAGES:
        raise ValueError(f"Unsupported language: {language}")
    changes = read_changes((root / "CHANGELOG.md").read_text(encoding="utf-8-sig"))
    selected = [language] if language else list(LANGUAGES)
    parts = [f"# KaraokeLink v{version}"]
    if language is None:
        parts.append(" · ".join(f"[{label}](#{code.lower()})" for code, label in LANGUAGES.items()))
    for code in selected:
        template = (root / "docs/release-notes" / f"{code}.md").read_text(encoding="utf-8-sig")
        if "{version}" not in template or "{changes}" not in template:
            raise ValueError(f"Release template is missing placeholders: {code}")
        body = template.replace("{version}", version).replace("{changes}", changes[code]).strip()
        parts.append(f'<a name="{code.lower()}"></a>\n\n## {LANGUAGES[code]}\n\n{body}')
    return "\n\n".join(parts) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version")
    parser.add_argument("--language", choices=list(LANGUAGES), help="Render one language; defaults to all five")
    parser.add_argument("--output", type=Path, help="Write UTF-8 Markdown directly to a file")
    args = parser.parse_args()
    try:
        result = render_release(args.version, language=args.language)
    except ValueError as error:
        parser.error(str(error))
    if args.output:
        args.output.write_text(result, encoding="utf-8")
    else:
        sys.stdout.reconfigure(encoding="utf-8")
        print(result, end="")


if __name__ == "__main__":
    main()
