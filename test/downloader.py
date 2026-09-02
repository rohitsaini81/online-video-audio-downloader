import yt_dlp


def download(url: str):
    options = {
        # Prefer H.264 video + M4A/AAC audio
        "format": "bestvideo[ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]/best[ext=mp4]",

        # Final container
        "merge_output_format": "mp4",

        # Filename
        "outtmpl": "%(title)s.%(ext)s",
    }

    with yt_dlp.YoutubeDL(options) as ydl:
        ydl.download([url])


if __name__ == "__main__":
    url = input("Paste video URL: ").strip()

    if not url:
        print("No URL provided.")
        raise SystemExit(1)

    try:
        download(url)
        print("\nDownload completed!")
    except Exception as e:
        print(f"\nDownload failed: {e}")
