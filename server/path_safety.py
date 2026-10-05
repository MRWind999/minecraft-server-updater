"""Path confinement helpers for the update service HTTP API."""

import os


def resolve_download_path(files_dir, filepath):
    """Return the absolute path of a servable file, or None when it escapes.

    A prefix comparison such as
    realpath(full).startswith(realpath(root)) wrongly accepts a sibling
    directory whose name shares the prefix (/data/files-secret for the root
    /data/files), so containment is checked with os.path.commonpath.
    """
    if not isinstance(filepath, str) or not filepath:
        return None
    safe_path = os.path.normpath(filepath).lstrip('/')
    if not safe_path or safe_path == '.' or safe_path == '..':
        return None
    full_path = os.path.join(files_dir, safe_path)
    try:
        root = os.path.realpath(files_dir)
        resolved = os.path.realpath(full_path)
        if os.path.commonpath([root, resolved]) != root:
            return None
    except ValueError:
        return None
    return full_path
