from unittest import TestCase

import numpy as np

from trajecta_worker.timeline import align_discrete_mode, build_output, compute_metrics


class TimelineTests(TestCase):
    def test_align_discrete_mode_forward_fill(self) -> None:
        timeline = np.array([0.0, 1.0, 2.0, 3.0], dtype=float)
        mode_data = {"t": [1.0, 3.0], "mode": ["GUIDED", "AUTO"]}

        aligned = align_discrete_mode(mode_data, timeline)

        self.assertEqual(aligned.tolist(), ["UNKNOWN", "GUIDED", "GUIDED", "AUTO"])

    def test_compute_metrics_basic(self) -> None:
        frames = [
            {
                "t": 0.0,
                "pos": {"lat": 55.0, "lon": 37.0, "alt": 100.0},
                "vel": 5.0,
                "imu": {"accel": [0.0, 0.0, 9.8]},
            },
            {
                "t": 10.0,
                "pos": {"lat": 55.0001, "lon": 37.0001, "alt": 110.0},
                "vel": 8.0,
                "imu": {"accel": [1.0, 2.0, 3.0]},
            },
        ]

        metrics = compute_metrics(frames)

        self.assertEqual(metrics.maxAltitude, 110.0)
        self.assertEqual(metrics.maxSpeed, 8.0)
        self.assertEqual(metrics.flightDuration, 10.0)
        self.assertIsNotNone(metrics.distance)
        self.assertGreater(metrics.distance or 0.0, 0.0)

    def test_build_output_sorts_events(self) -> None:
        data = {
            "gps": {"t": [1.0, 2.0], "lat": [55.0, 55.00001], "lon": [37.0, 37.00001], "alt": [100.0, 102.0], "speed": [7.0, 7.5], "vz": [1.5, 2.0]},
            "att": {"t": [1.0, 2.0], "roll": [0.0, 1.0], "pitch": [0.0, 0.5], "yaw": [179.0, 178.0]},
            "ctun": {"t": [1.0, 2.0], "throttle": [0.5, 0.55], "alt": [100.0, 102.0], "dalt": [0.0, 0.0], "crt": [1.8, 2.2]},
            "bat": {"t": [1.0, 2.0], "remainingPct": [92.0, 91.0]},
            "imu": {"t": [1.0, 2.0], "accx": [0.0, 0.1], "accy": [0.0, 0.1], "accz": [1.0, 1.1], "gyrx": [0.0, 0.0], "gyry": [0.0, 0.0], "gyrz": [0.0, 0.0]},
            "vibe": {"t": [1.0, 2.0], "x": [0.1, 0.1], "y": [0.2, 0.2], "z": [0.3, 0.3]},
            "pid": {"t": [1.0, 2.0], "roll": [0.1, 0.1], "pitch": [0.2, 0.2], "yaw": [0.3, 0.3]},
            "baro": {"t": [1.0, 2.0], "alt": [99.5, 101.0]},
            "gpa": {"t": [1.0, 2.0], "acc": [0.8, 0.9]},
            "mode": {"t": [1.0, 2.0], "mode": ["AUTO", "AUTO"]},
            "stat_rows": [],
            "pm_rows": [],
            "events": [
                {"t": 2.0, "type": "DISARMED", "eventId": 11, "code": "DISARMED", "description": "Vehicle disarmed", "severity": "info"},
                {"t": 1.0, "type": "ARMED", "eventId": 10, "code": "ARMED", "description": "Vehicle armed", "severity": "info"},
            ],
            "params": {"TEST": 1},
        }

        output = build_output(data, dt=0.1)

        self.assertEqual(output["meta"]["dt"], 0.1)
        self.assertGreaterEqual(len(output["frames"]), 1)
        self.assertEqual(output["events"][0]["type"], "ARMED")
        self.assertEqual(output["events"][0]["eventId"], 10)
        self.assertEqual(output["events"][0]["description"], "Vehicle armed")
        self.assertEqual(output["events"][1]["type"], "DISARMED")
        self.assertEqual(output["events"][1]["eventId"], 11)
        self.assertIsNotNone(output["frames"][0].get("battery"))
        self.assertIsNotNone(output["frames"][0].get("climbRate"))
        self.assertIn("parsing", output["meta"])
        self.assertIn("maxHorizontalSpeed", output["metrics"])
        self.assertIn("maxVerticalSpeed", output["metrics"])
        self.assertGreater(output["metrics"]["maxVerticalSpeed"] or 0.0, 0.0)
        self.assertIn("maxAcceleration", output["metrics"])
        self.assertIn("maxClimbRate", output["metrics"])

    def test_build_output_sanitizes_gps_spikes(self) -> None:
        data = {
            "gps": {
                "t": [0.0, 1.0, 2.0, 3.0],
                "lat": [55.0, 75.0, 55.0001, 55.0002],
                "lon": [37.0, 120.0, 37.0001, 37.0002],
                "alt": [100.0, 105.0, 102.0, 103.0],
                "speed": [6.0, 5000.0, 6.5, 7.0],
                "vz": [0.2, 300.0, 0.3, 0.2],
            },
            "att": {"t": [0.0, 3.0], "roll": [0.0, 0.0], "pitch": [0.0, 0.0], "yaw": [0.0, 0.0]},
            "ctun": {"t": [0.0, 3.0], "throttle": [0.4, 0.5], "alt": [100.0, 103.0], "dalt": [0.0, 0.0], "crt": [0.2, 0.2]},
            "bat": {"t": [0.0, 3.0], "remainingPct": [90.0, 89.0]},
            "imu": {"t": [0.0, 3.0], "accx": [0.0, 0.0], "accy": [0.0, 0.0], "accz": [1.0, 1.0], "gyrx": [0.0, 0.0], "gyry": [0.0, 0.0], "gyrz": [0.0, 0.0]},
            "vibe": {"t": [0.0, 3.0], "x": [0.1, 0.1], "y": [0.1, 0.1], "z": [0.1, 0.1]},
            "pid": {"t": [0.0, 3.0], "roll": [0.0, 0.0], "pitch": [0.0, 0.0], "yaw": [0.0, 0.0]},
            "baro": {"t": [0.0, 3.0], "alt": [100.0, 103.0]},
            "gpa": {"t": [0.0, 3.0], "acc": [0.0, 0.0]},
            "mode": {"t": [0.0, 3.0], "mode": ["AUTO", "AUTO"]},
            "stat_rows": [],
            "pm_rows": [],
            "events": [],
            "params": {},
        }

        output = build_output(data, dt=0.5)
        lats = [frame["pos"]["lat"] for frame in output["frames"] if frame["pos"]["lat"] is not None]
        lons = [frame["pos"]["lon"] for frame in output["frames"] if frame["pos"]["lon"] is not None]

        self.assertTrue(lats)
        self.assertTrue(lons)
        self.assertLess(max(lats), 56.0)
        self.assertLess(max(lons), 38.0)

    def test_build_output_recovers_after_single_gps_spike(self) -> None:
        data = {
            "gps": {
                "t": [0.0, 1.0, 2.0, 3.0],
                "lat": [55.0, 75.0, 55.00015, 55.00025],
                "lon": [37.0, 120.0, 37.00015, 37.00025],
                "alt": [100.0, 100.0, 101.0, 102.0],
                "speed": [6.0, 5000.0, 6.2, 6.4],
                "vz": [0.1, 250.0, 0.2, 0.2],
            },
            "att": {"t": [0.0, 3.0], "roll": [0.0, 0.0], "pitch": [0.0, 0.0], "yaw": [0.0, 0.0]},
            "ctun": {"t": [0.0, 3.0], "throttle": [0.4, 0.5], "alt": [100.0, 102.0], "dalt": [0.0, 0.0], "crt": [0.1, 0.2]},
            "bat": {"t": [0.0, 3.0], "remainingPct": [95.0, 94.0]},
            "imu": {"t": [0.0, 3.0], "accx": [0.0, 0.0], "accy": [0.0, 0.0], "accz": [1.0, 1.0], "gyrx": [0.0, 0.0], "gyry": [0.0, 0.0], "gyrz": [0.0, 0.0]},
            "vibe": {"t": [0.0, 3.0], "x": [0.1, 0.1], "y": [0.1, 0.1], "z": [0.1, 0.1]},
            "pid": {"t": [0.0, 3.0], "roll": [0.0, 0.0], "pitch": [0.0, 0.0], "yaw": [0.0, 0.0]},
            "baro": {"t": [0.0, 3.0], "alt": [100.0, 102.0]},
            "gpa": {"t": [0.0, 3.0], "acc": [0.0, 0.0]},
            "mode": {"t": [0.0, 3.0], "mode": ["AUTO", "AUTO"]},
            "stat_rows": [],
            "pm_rows": [],
            "events": [],
            "params": {},
        }

        output = build_output(data, dt=1.0)
        last = output["frames"][-1]["pos"]

        self.assertIsNotNone(last["lat"])
        self.assertIsNotNone(last["lon"])
        self.assertLess(abs(last["lat"] - 55.00025), 0.001)
        self.assertLess(abs(last["lon"] - 37.00025), 0.001)
