from unittest import TestCase
from unittest.mock import patch
import os

from trajecta_worker.models import AnalysisRequest, AnalysisStatus
from trajecta_worker.service import build_result_payload, process_request_message


class ServiceTests(TestCase):
    def test_build_result_payload_with_metrics(self) -> None:
        request = AnalysisRequest(taskId=7)
        with patch("trajecta_worker.service.download_raw_for_task", return_value=("tmp.bin", False)), patch(
            "trajecta_worker.service.parse_log", return_value={}
        ), patch(
            "trajecta_worker.service.build_output",
            return_value={
                "metrics": {
                    "maxAltitude": 120.5,
                    "maxSpeed": 18.2,
                    "flightDuration": 53.0,
                    "distance": 400.0,
                }
            },
        ):
            result = process_request_message(request, dt=0.1)

        payload = build_result_payload(result)
        self.assertEqual(payload["taskId"], 7)
        self.assertEqual(payload["status"], AnalysisStatus.COMPLETED)
        self.assertIsNone(payload.get("trajectoryObjectKey"))
        self.assertIn("trajectoryJson", payload)
        self.assertIn("metrics", payload)
        self.assertEqual(payload["metrics"]["maxAltitude"], 120.5)

    def test_process_request_message_success_and_cleanup(self) -> None:
        request = AnalysisRequest(taskId=42)
        output_payload = {"metrics": {"maxAltitude": 10.0, "maxSpeed": 2.0, "flightDuration": 1.0, "distance": 3.0}}

        with patch("trajecta_worker.service.download_raw_for_task", return_value=("C:/tmp/in.bin", True)), patch(
            "trajecta_worker.service.parse_log", return_value={"dummy": "data"}
        ), patch("trajecta_worker.service.build_output", return_value=output_payload), patch(
            "trajecta_worker.service.os.remove"
        ) as remove_mock:
            result = process_request_message(request, dt=0.1)

        self.assertEqual(result.status, AnalysisStatus.COMPLETED)
        self.assertIsNotNone(result.trajectoryJson)
        remove_mock.assert_called_once_with("C:/tmp/in.bin")

    def test_process_request_message_failure_returns_failed(self) -> None:
        request = AnalysisRequest(taskId=99)

        with patch("trajecta_worker.service.download_raw_for_task", side_effect=RuntimeError("download failed")):
            result = process_request_message(request, dt=0.1)

        self.assertEqual(result.status, AnalysisStatus.FAILED)
        self.assertIn("download failed", result.errorMessage or "")

    def test_process_request_message_fails_on_invalid_dt(self) -> None:
        request = AnalysisRequest(taskId=100)

        result = process_request_message(request, dt=0.0)

        self.assertEqual(result.status, AnalysisStatus.FAILED)
        self.assertIn("dt must be greater than zero", result.errorMessage or "")

    def test_process_request_message_passes_limits_to_parsing_pipeline(self) -> None:
        request = AnalysisRequest(taskId=101)

        with patch.dict(
            os.environ,
            {"MAX_PARSE_MESSAGES": "123", "MAX_BAD_DATA_MESSAGES": "7", "MAX_TIMELINE_FRAMES": "456"},
            clear=False,
        ), patch(
            "trajecta_worker.service.download_raw_for_task", return_value=("tmp.bin", False)
        ), patch("trajecta_worker.service.parse_log", return_value={"gps": {"t": [0.0]}}) as parse_mock, patch(
            "trajecta_worker.service.build_output", return_value={"metrics": {}}
        ) as output_mock:
            result = process_request_message(request, dt=0.1)

        self.assertEqual(result.status, AnalysisStatus.COMPLETED)
        parse_mock.assert_called_once_with("tmp.bin", max_messages=123, max_bad_data_messages=7)
        output_mock.assert_called_once_with({"gps": {"t": [0.0]}}, dt=0.1, max_frames=456)

    def test_process_request_message_fails_on_timeline_overflow(self) -> None:
        request = AnalysisRequest(taskId=102)

        with patch("trajecta_worker.service.download_raw_for_task", return_value=("tmp.bin", False)), patch(
            "trajecta_worker.service.parse_log", return_value={"gps": {"t": [0.0, 1.0]}}
        ), patch("trajecta_worker.service.build_output", side_effect=ValueError("timeline is too large")):
            result = process_request_message(request, dt=0.1)

        self.assertEqual(result.status, AnalysisStatus.FAILED)
        self.assertIn("timeline is too large", result.errorMessage or "")
