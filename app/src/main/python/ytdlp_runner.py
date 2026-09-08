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
            downloaded = int(data.get("downloaded_bytes") or 0)
            total = int(data.get("total_bytes") or data.get("total_bytes_estimate") or 0)
            speed = float(data.get("speed") or 0)
            percent = (downloaded / total * 100.0) if total else 0.0
            callback.log(
                "PROGRESS:"
                + json.dumps(
                    {
                        "percent": percent,
                        "downloaded": downloaded,
                        "total": total,
                        "speed": speed,
                    }
                )
            )
            percent_str = data.get("_percent_str", "").strip()
            speed_str = data.get("_speed_str", "").strip()
            eta = data.get("_eta_str", "").strip()
            parts = [value for value in (percent_str, speed_str, f"ETA {eta}" if eta else "") if value]
            if parts:
                callback.log("Downloading: " + " | ".join(parts))
        elif status == "finished":
            callback.log("Download data complete; finalizing file...")

    return report


def _source_from_info(info, url):
    extractor = (info.get("extractor_key") or info.get("extractor") or "").lower()
    lowered = (url or "").lower()
    if "youtu" in extractor or "youtu" in lowered:
        return "YouTube"
    if "instagram" in extractor or "instagram" in lowered:
        return "Instagram"
    if "tiktok" in extractor or "tiktok" in lowered:
        return "TikTok"
    if "facebook" in extractor or "facebook" in lowered:
        return "Facebook"
    if extractor in ("twitter", "x") or "x.com" in lowered or "twitter" in lowered:
        return "X"
    if extractor:
        return info.get("extractor_key") or info.get("extractor")
    return "Web"


def _size_label(nbytes):
    if not nbytes:
        return ""
    mb = nbytes / (1024 * 1024)
    if mb >= 10:
        return f"{mb:.0f} MB"
    if mb >= 1:
        return f"{mb:.1f} MB"
    kb = nbytes / 1024
    return f"{kb:.0f} KB"


def _best_entry(current, candidate):
    current_size = (current or {}).get("filesize") or 0
    candidate_size = candidate.get("filesize") or 0
    if current is None or candidate_size >= current_size:
        return candidate
    return current


def extract_info(url, callback):
    callback.log("Extracting media information...")

    options = {
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "logger": _AndroidLogger(callback),
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=False)

    raw_formats = info.get("formats") or []
    video_by_height = {}
    best_audio = None

    for item in raw_formats:
        vcodec = item.get("vcodec") or "none"
        acodec = item.get("acodec") or "none"
        height = item.get("height")
        size = item.get("filesize") or item.get("filesize_approx") or 0
        entry = {
            "format_id": item.get("format_id"),
            "ext": item.get("ext") or "mp4",
            "height": height,
            "filesize": int(size) if size else 0,
            "abr": item.get("abr") or 0,
        }
        if vcodec != "none" and height:
            video_by_height[int(height)] = _best_entry(video_by_height.get(int(height)), entry)
        if acodec != "none" and vcodec == "none":
            best_audio = _best_entry(best_audio, entry)

    audio_size = (best_audio or {}).get("filesize") or 0
    options_out = []
    added_heights = set()

    def add_video_option(target_height, label):
        candidates = [
            value
            for height, value in video_by_height.items()
            if height <= target_height and height not in added_heights
        ]
        if not candidates:
            return
        chosen = max(candidates, key=lambda value: value["height"])
        added_heights.add(chosen["height"])
        total_size = (chosen.get("filesize") or 0) + audio_size
        options_out.append(
            {
                "id": f"video_{target_height}",
                "category": "video",
                "label": label,
                "max_height": target_height,
                "audio_only": False,
                "convert_mp3": False,
                "size_bytes": total_size,
                "size_label": _size_label(total_size),
            }
        )

    add_video_option(1080, "1080p Full HD (MP4)")
    add_video_option(720, "720p HD (MP4)")
    add_video_option(480, "480p (MP4)")

    if not options_out:
        options_out.append(
            {
                "id": "video_best",
                "category": "video",
                "label": "Best Video (MP4)",
                "max_height": 0,
                "audio_only": False,
                "convert_mp3": False,
                "size_bytes": 0,
                "size_label": "",
            }
        )

    mp3_size = int(audio_size * 0.85) if audio_size else 0
    options_out.append(
        {
            "id": "audio_mp3",
            "category": "audio",
            "label": "High Quality (320kbps MP3)",
            "max_height": 0,
            "audio_only": True,
            "convert_mp3": True,
            "size_bytes": mp3_size,
            "size_label": _size_label(mp3_size),
        }
    )
    options_out.append(
        {
            "id": "audio_m4a",
            "category": "audio",
            "label": "Custom Audio (M4A)",
            "max_height": 0,
            "audio_only": True,
            "convert_mp3": False,
            "size_bytes": audio_size,
            "size_label": _size_label(audio_size),
        }
    )

    return json.dumps(
        {
            "title": info.get("title") or "Untitled",
            "source": _source_from_info(info, url),
            "thumbnail": info.get("thumbnail") or "",
            "duration": info.get("duration") or 0,
            "webpage_url": info.get("webpage_url") or url,
            "options": options_out,
        }
    )


def _resolve_filepath(ydl, info):
    requested = info.get("requested_downloads") or []
    if requested:
        path = requested[0].get("filepath")
        if path:
            return path

    prepared = ydl.prepare_filename(info)
    path = pathlib.Path(prepared)
    if path.exists():
        return str(path)

    ext = info.get("ext")
    if ext:
        alt = path.with_suffix("." + ext)
        if alt.exists():
            return str(alt)

    matches = list(path.parent.glob(path.stem + ".*"))
    if matches:
        return str(matches[0])
    return prepared


def _download_payload(filepath, output_path, info):
    path = pathlib.Path(filepath)
    size = path.stat().st_size if path.is_file() else 0
    return json.dumps(
        {
            "status": "ok",
            "filepath": str(path),
            "output_dir": str(output_path),
            "title": info.get("title") or path.stem,
            "ext": path.suffix.lstrip("."),
            "filesize": size,
        }
    )


def download(url, output_dir, mode, callback):
    output_path = pathlib.Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)

    if mode == "audio":
        format_selector = "bestaudio/best[acodec!=none]"
    else:
        format_selector = "bestvideo/best[vcodec!=none]"

    callback.log(f"Mode: {mode}")
    callback.log("Extracting media information...")

    options = {
        "outtmpl": str(output_path / "%(title)s.%(ext)s"),
        "format": format_selector,
        "noplaylist": True,
        "logger": _AndroidLogger(callback),
        "progress_hooks": [_progress_hook(callback)],
        "quiet": False,
        "no_warnings": False,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=True)
        filename = _resolve_filepath(ydl, info)

    return _download_payload(filename, output_path, info)


def download_track(url, output_dir, mode, callback, max_height=0):
    output_path = pathlib.Path(output_dir)
    output_path.mkdir(parents=True, exist_ok=True)
    max_height = int(max_height or 0)

    if mode == "audio":
        format_selector = "bestaudio/best[acodec!=none]"
    elif mode == "video":
        if max_height > 0:
            format_selector = (
                f"bestvideo[height<={max_height}]/"
                f"best[height<={max_height}]/"
                "bestvideo/best[vcodec!=none]"
            )
        else:
            format_selector = "bestvideo/best[vcodec!=none]"
    else:
        raise ValueError(f"Unsupported mode: {mode}")

    callback.log(f"Track mode: {mode}" + (f" (<= {max_height}p)" if max_height else ""))
    callback.log("Extracting media information...")

    options = {
        "outtmpl": str(output_path / "%(title)s.%(ext)s"),
        "format": format_selector,
        "noplaylist": True,
        "logger": _AndroidLogger(callback),
        "progress_hooks": [_progress_hook(callback)],
        "quiet": False,
        "no_warnings": False,
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        info = ydl.extract_info(url, download=True)
        filename = _resolve_filepath(ydl, info)

    return _download_payload(filename, output_path, info)
