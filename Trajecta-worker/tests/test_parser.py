from unittest import TestCase
from unittest.mock import patch

from trajecta_worker.models import WorkerError
from trajecta_worker.parser import parse_log


class _FakeMessage:
    def __init__(self, mtype: str, payload: dict):
        self._mtype = mtype
        self._payload = payload

    def get_type(self) -> str:
        return self._mtype

    def to_dict(self) -> dict:
        return self._payload


class _FakeLog:
    def __init__(self, messages: list[_FakeMessage]):
        self._messages = messages
        self._index = 0

    def recv_match(self, blocking: bool = False):
        if self._index >= len(self._messages):
            return None
        message = self._messages[self._index]
        self._index += 1
        return message


class ParserTests(TestCase):
    def test_parse_log_fails_when_bad_data_exceeds_threshold(self) -> None:
        messages = [
            _FakeMessage("BAD_DATA", {}),
            _FakeMessage("BAD_DATA", {}),
            _FakeMessage("BAD_DATA", {}),
        ]

        with patch("trajecta_worker.parser.validate_dataflash_signature"), patch(
            "trajecta_worker.parser.mavutil.mavlink_connection", return_value=_FakeLog(messages)
        ):
            with self.assertRaises(WorkerError) as error:
                parse_log("broken.bin", max_bad_data_messages=2)

        self.assertIn("too many invalid message headers", str(error.exception))

    def test_parse_log_allows_small_bad_data_and_keeps_valid_records(self) -> None:
        messages = [
            _FakeMessage("BAD_DATA", {}),
            _FakeMessage(
                "GPS",
                {
                    "TimeUS": 1_000_000,
                    "Lat": 550000000,
                    "Lng": 370000000,
                    "Alt": 100,
                    "Spd": 5,
                    "VZ": 0,
                },
            ),
        ]

        with patch("trajecta_worker.parser.validate_dataflash_signature"), patch(
            "trajecta_worker.parser.mavutil.mavlink_connection", return_value=_FakeLog(messages)
        ):
            parsed = parse_log("mostly-ok.bin", max_bad_data_messages=5)

        self.assertEqual(len(parsed["gps"]["t"]), 1)

