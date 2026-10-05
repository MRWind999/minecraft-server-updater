"""Regression tests for the /api/files path confinement helper."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from path_safety import resolve_download_path

# The helper only inspects paths, so the checks use absolute paths that need
# not exist on disk. This keeps the suite runnable in restricted sandboxes and
# inside the container image.
FILES_DIR = os.path.abspath(os.path.join(os.sep, 'data', 'files'))


class ResolveDownloadPathTest(unittest.TestCase):

    def test_managed_file_is_served(self):
        resolved = resolve_download_path(FILES_DIR, 'mods/a.jar')
        self.assertEqual(resolved, os.path.join(FILES_DIR, 'mods', 'a.jar'))

    def test_nested_file_is_served(self):
        self.assertIsNotNone(resolve_download_path(FILES_DIR, 'config/deep/options.txt'))

    def test_parent_traversal_is_rejected(self):
        self.assertIsNone(resolve_download_path(FILES_DIR, '../../etc/passwd'))

    def test_prefix_sibling_is_rejected(self):
        # A sibling whose name shares the prefix (files-secret for files) must
        # not pass a plain string-prefix comparison.
        self.assertIsNone(resolve_download_path(FILES_DIR, '../files-secret/secret.cfg'))

    def test_empty_path_is_rejected(self):
        self.assertIsNone(resolve_download_path(FILES_DIR, ''))

    def test_dot_path_is_rejected(self):
        self.assertIsNone(resolve_download_path(FILES_DIR, '.'))

    def test_parent_only_path_is_rejected(self):
        self.assertIsNone(resolve_download_path(FILES_DIR, '..'))


if __name__ == '__main__':
    unittest.main()
