import pathlib
import sys
import asyncio
import tempfile
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))

from core import adblock
from core.adblock import parse_hosts_text


class ParseHostsTextTests(unittest.TestCase):
    def test_parses_hosts_and_bare_domains_and_deduplicates(self):
        text = """
        # comment
        0.0.0.0 ads.example.com
        127.0.0.1 tracker.example.com # inline comment
        plain.example.org
        ads.example.com
        EXAMPLE.NET.
        """

        self.assertEqual(
            parse_hosts_text(text),
            [
                "ads.example.com",
                "tracker.example.com",
                "plain.example.org",
                "example.net",
            ],
        )

    def test_skips_invalid_reserved_and_wildcard_entries(self):
        text = """
        localhost
        localhost.localdomain
        192.168.1.1
        ::1
        analytics-*.example.com
        invalid
        bad_domain.example
        host name.example.com
        """

        self.assertEqual(parse_hosts_text(text), [])


class LoadAdblockSourcesTests(unittest.TestCase):
    def test_loads_local_file_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            source = pathlib.Path(tmp) / "snapshot.txt"
            source.write_text(
                "0.0.0.0 ads.example.com\ntracker.example.org\n",
                encoding="utf-8",
            )

            self.assertEqual(
                adblock.load_all([str(source)]),
                ["ads.example.com", "tracker.example.org"],
            )

    def test_startup_keeps_local_snapshot_when_url_cache_is_missing(self):
        with tempfile.TemporaryDirectory() as tmp:
            cache_dir = pathlib.Path(tmp) / "cache"
            source = pathlib.Path(tmp) / "snapshot.txt"
            source.write_text("0.0.0.0 local.example.com\n", encoding="utf-8")

            with (
                mock.patch.object(adblock, "_CACHE_DIR", cache_dir),
                mock.patch.object(adblock, "_fetch") as fetch,
            ):
                domains = adblock.load_all([
                    str(source),
                    "https://example.invalid/list.txt",
                ])

            self.assertEqual(domains, ["local.example.com"])
            fetch.assert_not_called()

    def test_refresh_downloads_and_caches_update_url(self):
        url = "https://example.test/list.txt"

        with tempfile.TemporaryDirectory() as tmp:
            cache_dir = pathlib.Path(tmp) / "cache"

            with (
                mock.patch.object(adblock, "_CACHE_DIR", cache_dir),
                mock.patch.object(
                    adblock,
                    "_fetch",
                    return_value="0.0.0.0 updated.example.com\n",
                ),
            ):
                callbacks: list[list[str]] = []
                domains = asyncio.run(
                    adblock.refresh_all([url], callback=callbacks.append)
                )

                self.assertEqual(domains, ["updated.example.com"])
                self.assertEqual(callbacks, [["updated.example.com"]])
                self.assertEqual(
                    adblock.load_all([url]),
                    ["updated.example.com"],
                )


if __name__ == "__main__":
    unittest.main()
