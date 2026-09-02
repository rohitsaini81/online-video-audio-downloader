import json
import pathlib
import sys

import yt_dlp


def probe():
    return json.dumps(
        {
            "python_version": sys.version.split()[0],
            "yt_dlp_version": yt_dlp.version.__version__,
            "yt_dlp_path": yt_dlp.__file__,
        }
    )


class _AndroidLogger:
    def __init__(self, callback):
        self.callback = callback

    def _send(self, message):
        text = str(message).strip()
        if text:
            self.callback.log(text)

    def debug(self, message):
        # yt-dlp sends normal console output through debug as well as diagnostics.
        self._send(message)

    def info(self, message):
        self._send(message)

    def warning(self, message):
        self._send(f"WARNING: {message}")

    def error(self, message):
        self._send(f"ERROR: {message}")


def _progress_hook(callback):
    def report(data):
        status = data.get("status")
        if status == "downloading":
            percent = data.get("_percent_str", "").strip()
            speed = data.get("_speed_str", "").strip()
            eta = data.get("_eta_str", "").strip()
            parts = [value for value in (percent, speed, f"ETA {eta}" if eta else "") if value]
            callback.log("Downloading: " + " | ".join(parts))
        elif status == "finished":
            callback.log("Download data complete; finalizing file...")

    return report


def download(url, output_dir, mode, callback):
    output_path = pathlib.Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    if mode == "audio":
        format_selector = "bestaudio/best[acodec!=none]"
    else:
        format_selector = (
            "best[ext=mp4][vcodec!=none][acodec!=none]/"
            "best[vcodec!=none][acodec!=none]/"
            "bestvideo/bestaudio"
        )

    callback.log(f"Mode: {mode}")
    callback.log("Extracting media information...")

    options = {
        "outtmpl": str(output_path / "%(title)s.%(ext)s"),
        # FFmpeg isn't bundled yet, so prefer a format which already contains
        # both video and audio. Some sites don't offer a progressive MP4, so
        # fall back to another combined format, then a video-only or audio-only
        # format rather than failing with "Requested format is not available".
        "format": format_selector,
        "noplaylist": True,
        "logger": _AndroidLogger(callback),
        "progress_hooks": [_progress_hook(callback)],
        "quiet": False,
        "no_warnings": False,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=True)
        filename = ydl.prepare_filename(info)

    return json.dumps(
        {
            "status": "ok",
            "filepath": filename,
            "output_dir": str(output_path),
        }
    )
