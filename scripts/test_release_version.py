import unittest
from release_version import choose_version

class ReleaseVersionTest(unittest.TestCase):
    def test_first_official_release_ignores_previews(self):
        self.assertEqual(("0.2.0", 2000), choose_version("0.2.0", ["preview-2", "preview-3"]))
    def test_next_push_increments_patch(self):
        self.assertEqual(("0.2.1", 2001), choose_version("0.2.0", ["v0.2.0"]))
    def test_rerun_reuses_version_on_same_commit(self):
        self.assertEqual(("0.2.0", 2000), choose_version("0.2.0", ["v0.2.0"], ["v0.2.0"]))
    def test_manual_minor_bump_takes_precedence(self):
        self.assertEqual(("0.3.0", 3000), choose_version("0.3.0", ["v0.2.8"]))
    def test_versions_sort_numerically(self):
        self.assertEqual(("0.2.11", 2011), choose_version("0.2.0", ["v0.2.9", "v0.2.10"]))
    def test_patch_overflow_remains_monotonic(self):
        self.assertEqual(("0.3.0", 3000), choose_version("0.2.0", ["v0.2.999"]))
    def test_rejects_invalid_base(self):
        with self.assertRaises(ValueError):
            choose_version("latest", [])

if __name__ == "__main__":
    unittest.main()