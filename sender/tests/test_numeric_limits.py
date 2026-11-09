import unittest
import studio as S
import workspace_model as WM


class NumericLimits(unittest.TestCase):
    def test_values_beyond_screen_are_kept(self):
        l = S.normalize_layer({"kind": "text", "x": -3, "y": 7.5, "w": 4, "h": 2.5, "size": 1.2})
        self.assertEqual((l["x"], l["y"], l["w"], l["h"], l["size"]), (-3, 7.5, 4, 2.5, 1.2))

    def test_garbage_falls_back(self):
        l = S.normalize_layer({"kind": "text", "x": float("inf"), "w": "abc", "h": float("nan")})
        self.assertEqual((l["x"], l["w"], l["h"]), (0.1, 0.8, 0.2))

    def test_video_box_and_layers_only_cells_exist(self):
        self.assertIn("media.box", WM.panel_items("group:media"))
        self.assertIn("text.sendonly", WM.panel_items("group:text"))
        self.assertTrue(WM.is_valid("action:send_layers")); self.assertIn("project.rbg", WM.panel_items("group:project"))


if __name__ == "__main__":
    unittest.main()
