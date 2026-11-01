"""Run boundary contracts without requiring MediaPipe/OpenCV or real patient photos."""
import ast
from pathlib import Path
import re
import tempfile
import unittest
import uuid
import numpy as np

ROOT = Path(__file__).resolve().parents[1]


def load_function(filename, function, env):
    # Execute the actual function body while avoiding optional image-stack imports.
    tree = ast.parse((ROOT / filename).read_text(encoding="utf-8-sig"))
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == function)
    exec(compile(ast.Module(body=[node], type_ignores=[]), filename, "exec"), env)
    return env[function]


class HttpError(Exception):
    def __init__(self, status_code, detail):
        super().__init__(detail)
        self.status_code = status_code


class ServerContracts(unittest.TestCase):
    def test_paths_are_bounded_and_ids_are_reserved(self):
        with tempfile.TemporaryDirectory() as directory:
            env = {"LOG_DIR": Path(directory), "re": re, "uuid": uuid, "HTTPException": HttpError}
            validate = load_function("server.py", "validated_session", env)
            for bad in ["../outside", "a/b", "a\\b", "/tmp/out", "a" * 81]:
                with self.assertRaises(HttpError) as failure:
                    validate(bad, "B")
                self.assertEqual(400, failure.exception.status_code)
            first, second = validate("", "B"), validate("", "B")
            self.assertNotEqual(first, second)
            self.assertTrue((Path(directory) / first).is_dir())
            with self.assertRaises(HttpError) as failure:
                validate(first, "B")
            self.assertEqual(409, failure.exception.status_code)

    def test_card_corners_give_same_scale_after_rotation_and_reordering(self):
        scale = load_function("measure_arm.py", "card_scale_from_corners", {"np": np, "CARD_MM": (85.60, 53.98)})
        q = np.array([[0, 0], [856, 0], [856, 539.8], [0, 539.8]])
        angle = np.deg2rad(31)
        rotation = np.array([[np.cos(angle), -np.sin(angle)], [np.sin(angle), np.cos(angle)]])
        for points in [q, (q @ rotation.T + [500, 300])[[2, 0, 3, 1]]]:
            mm_per_px, _, ratio_error = scale(points)
            self.assertAlmostEqual(0.1, mm_per_px)
            self.assertAlmostEqual(0, ratio_error)


if __name__ == "__main__":
    unittest.main()
