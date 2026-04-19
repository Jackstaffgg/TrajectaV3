from unittest import TestCase
from unittest.mock import patch

from trajecta_worker.models import WorkerError
from trajecta_worker.storage import download_raw_for_task


class _FakeResponse:
    def __init__(self, chunks: list[bytes], headers: dict[str, str] | None = None) -> None:
        self._chunks = chunks
        self._index = 0
        self.headers = headers or {}

    def read(self, _size: int) -> bytes:
        if self._index >= len(self._chunks):
            return b""
        value = self._chunks[self._index]
        self._index += 1
        return value

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:
        return False


class StorageTests(TestCase):
    def test_download_raw_for_task_rejects_large_content_length(self) -> None:
        with patch.dict(
            "os.environ",
            {
                "INTERNAL_WORKER_TOKEN": "secret",
                "MAX_RAW_DOWNLOAD_BYTES": "10",
            },
            clear=False,
        ), patch(
            "trajecta_worker.storage.urlopen",
            return_value=_FakeResponse([b"abc"], headers={"Content-Length": "11"}),
        ):
            with self.assertRaises(WorkerError):
                download_raw_for_task(1)

    def test_download_raw_for_task_rejects_large_stream(self) -> None:
        with patch.dict(
            "os.environ",
            {
                "INTERNAL_WORKER_TOKEN": "secret",
                "MAX_RAW_DOWNLOAD_BYTES": "5",
                "DOWNLOAD_CHUNK_SIZE_BYTES": "2",
            },
            clear=False,
        ), patch(
            "trajecta_worker.storage.urlopen",
            return_value=_FakeResponse([b"ab", b"cd", b"ef"]),
        ):
            with self.assertRaises(WorkerError):
                download_raw_for_task(1)
