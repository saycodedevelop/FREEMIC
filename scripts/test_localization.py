"""Guard translated strings against missing entries and broken format arguments."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

RES = Path(__file__).resolve().parents[1] / "app/src/main/res"

class LocalizationTest(unittest.TestCase):
    def test_all_languages_are_complete_and_preserve_format_arguments(self):
        def read(folder):
            items = ET.parse(RES / folder / "strings.xml").getroot().findall("string")
            self.assertEqual(len(items), len({item.attrib["name"] for item in items}))
            return {item.attrib["name"]: item.text or "" for item in items}
        base = read("values")
        for folder in ["values-th", "values-zh-rCN", "values-vi", "values-in"]:
            translated = read(folder)
            self.assertEqual(set(base), set(translated), folder)
            for key, value in translated.items():
                self.assertTrue(value.strip(), (folder, key))
                self.assertNotIn("???", value, (folder, key))
                placeholders = lambda text: sorted(re.findall(r"%[0-9]+\$[sd]", text))
                self.assertEqual(placeholders(base[key]), placeholders(value), (folder, key))
