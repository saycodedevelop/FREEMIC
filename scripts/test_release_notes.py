"""Publishing regressions: translations must be complete and stay in the right language."""
import re
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from release_notes import LANGUAGES, ROOT, read_changes, render_release

README_FILES = ["README.md", "README.en.md", "README.zh-CN.md", "README.vi.md", "README.id.md"]


class ReleaseNotesTest(unittest.TestCase):
    def sample(self):
        return "# Changelog\n\n## [Unreleased]\n\n" + "\n".join(
            f"### [{code}] {label}\n\n- Current change: {code}\n"
            for code, label in LANGUAGES.items()
        ) + "\n## [0.1.0]\n\n- Old change\n"

    def test_only_unreleased_changes_are_selected(self):
        changes = read_changes(self.sample())
        self.assertEqual(set(LANGUAGES), set(changes))
        for code, body in changes.items():
            self.assertEqual(f"- Current change: {code}", body)
            self.assertNotIn("Old change", body)

    def test_missing_empty_duplicate_and_unknown_translations_fail(self):
        sample = self.sample()
        variants = [
            sample.replace("### [id] Bahasa Indonesia\n\n- Current change: id\n", ""),
            sample.replace("- Current change: id", ""),
            sample.replace("## [0.1.0]", "### [en] English\n\n- Duplicate\n\n## [0.1.0]"),
            sample.replace("### [id]", "### [xx]"),
            "# Changelog\n\n## [0.1.0]\n",
        ]
        for value in variants:
            with self.subTest(value=value), self.assertRaises(ValueError):
                read_changes(value)

    def test_full_release_contains_all_languages_versions_and_anchors(self):
        body = render_release("0.4.1")
        self.assertTrue(body.startswith("# KaraokeLink v0.4.1\n"))
        self.assertNotIn("{changes}", body)
        self.assertNotIn("{version}", body)
        self.assertEqual(5, body.count("KaraokeLink-0.4.1.apk"))
        for code, label in LANGUAGES.items():
            self.assertIn(f"[{label}](#{code.lower()})", body)
            self.assertIn(f'<a name="{code.lower()}"></a>', body)
            self.assertIn(f"## {label}\n", body)
        self.assertNotIn("## [0.3.1]", body)

    def test_single_language_has_only_its_own_changes(self):
        for code, label in LANGUAGES.items():
            body = render_release("0.4.1", language=code)
            self.assertEqual(1, body.count("KaraokeLink-0.4.1.apk"))
            changes = read_changes((ROOT / "CHANGELOG.md").read_text(encoding="utf-8-sig"))
            self.assertIn(changes[code], body)
            for other in set(LANGUAGES) - {code}:
                self.assertNotIn(changes[other], body)

    def test_invalid_versions_and_languages_fail(self):
        for version in ["latest", "0.4.1\nmalformed", "1.2", "<script>"]:
            with self.subTest(version=version), self.assertRaises(ValueError):
                render_release(version)
        with self.assertRaises(ValueError):
            render_release("0.4.1", language="xx")

    def test_cli_writes_utf8_from_another_working_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "notes.md"
            subprocess.run(
                [sys.executable, str(ROOT / "scripts/release_notes.py"), "0.4.1", "--output", str(output)],
                cwd=directory, check=True, capture_output=True,
            )
            self.assertEqual(render_release("0.4.1"), output.read_text(encoding="utf-8"))

    def test_readme_language_links_and_local_files_exist(self):
        for filename in README_FILES:
            text = (ROOT / filename).read_text(encoding="utf-8-sig")
            for target in README_FILES:
                self.assertIn(f"]({target})", text, filename)
            links = re.findall(r"\]\(([^)]+)\)", text) + re.findall(r'src="([^"]+)"', text)
            for link in links:
                if "://" in link or link.startswith("#"):
                    continue
                self.assertTrue((ROOT / link.split("#", 1)[0]).is_file(), (filename, link))


if __name__ == "__main__":
    unittest.main()
