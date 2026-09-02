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


def download(url, output_dir):
    output_path = pathlib.Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    options = {
        "outtmpl": str(output_path / "%(title)s.%(ext)s"),
        # FFmpeg isn't bundled yet, so prefer a format which already contains
        # both video and audio. Some sites don't offer a progressive MP4, so
        # fall back to another combined format, then a video-only or audio-only
        # format rather than failing with "Requested format is not available".
        "format": (
            "best[ext=mp4][vcodec!=none][acodec!=none]/"
            "best[vcodec!=none][acodec!=none]/"
            "bestvideo/bestaudio"
        ),
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
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
