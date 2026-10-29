#!/usr/bin/env python3
"""Animate Fink LSST DIA or Solar-System source cutouts in observation-time order.

Requires Pillow: python -m pip install Pillow
Example: python animate.py 21163615905265241 --max-frames 60
The default includes every source (up to the safety limit), not an arbitrary API
prefix. --max-frames samples the entire time span, including its endpoints.
"""

from __future__ import annotations

import argparse
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
import io
import json
import math
import os
from pathlib import Path
import re
import sys
import tempfile
from urllib import error, parse, request

API = "https://api.lsst.fink-portal.org"
MAX_SOURCES = 1200
MAX_JSON_BYTES = 16 * 1024 * 1024
MAX_CUTOUT_BYTES = 2 * 1024 * 1024
MAX_FRAME_RGB_BYTES = 96 * 1024 * 1024
IDENTIFIER = re.compile(r"[0-9]{1,24}\Z")


@dataclass(frozen=True)
class Source:
    source_id: str
    mjd: float
    band: str


def identifier(value: object, field: str) -> str:
    """Preserve large JSON integer IDs exactly and reject ambiguous encodings."""
    if isinstance(value, bool) or not isinstance(value, (int, str)):
        raise ValueError(f"Invalid {field}: expected a decimal identifier")
    result = str(value)
    if not IDENTIFIER.fullmatch(result):
        raise ValueError(f"Invalid {field}: expected a decimal identifier")
    return result


class FinkClient:
    """Bounded HTTPS-only client; API paths and destination are fixed in source."""

    def __init__(self, timeout: float = 45):
        self.timeout = timeout
        self.opener = request.build_opener(_NoRedirect())

    def _read(self, req: request.Request, limit: int) -> bytes:
        try:
            with self.opener.open(req, timeout=self.timeout) as response:
                data = response.read(limit + 1)
                if len(data) > limit:
                    raise ValueError(f"Oversized response from {req.full_url}")
                return data
        except error.HTTPError as exc:
            raise RuntimeError(f"Fink HTTP {exc.code} at {req.full_url}") from exc
        except error.URLError as exc:
            raise RuntimeError(f"Fink request failed at {req.full_url}: {exc.reason}") from exc

    def post_json(self, path: str, body: dict) -> object:
        if path not in {"/api/v1/sources", "/api/v1/resolver", "/api/v1/sso"}:
            raise ValueError("Unexpected Fink API path")
        req = request.Request(API + path, data=json.dumps(body).encode("utf-8"),
                              headers={"Content-Type": "application/json", "Accept": "application/json"})
        return json.loads(self._read(req, MAX_JSON_BYTES))

    def cutout(self, source_id: str, kind: str) -> bytes:
        source_id = identifier(source_id, "diaSourceId")
        if kind not in {"Science", "Template", "Difference"}:
            raise ValueError("Unknown cutout kind")
        url = API + "/api/v1/cutouts?" + parse.urlencode({"diaSourceId": source_id, "kind": kind})
        return self._read(request.Request(url, headers={"Accept": "image/png"}), MAX_CUTOUT_BYTES)


class _NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def _sources(rows: object, object_id: str, identity_field: str) -> list[Source]:
    if not isinstance(rows, list) or len(rows) >= MAX_SOURCES:
        raise ValueError(f"Invalid or truncated source history (limit {MAX_SOURCES - 1} rows)")
    if not rows:
        raise ValueError("No sources for the requested object")
    found: dict[str, Source] = {}
    for row in rows:
        if not isinstance(row, dict) or identifier(row.get(identity_field), identity_field) != object_id:
            raise ValueError("Source identity does not match the requested object")
        source_id = identifier(row.get("r:diaSourceId"), "diaSourceId")
        mjd = row.get("r:midpointMjdTai")
        if isinstance(mjd, bool) or not isinstance(mjd, (float, int)) or not math.isfinite(mjd) or mjd <= 0:
            raise ValueError(f"Invalid MJD for source {source_id}")
        band = row.get("r:band", "?")
        if not isinstance(band, str) or len(band) > 8 or any(ord(c) < 32 for c in band):
            raise ValueError(f"Invalid band for source {source_id}")
        if source_id in found:
            raise ValueError(f"Duplicate source {source_id}")
        found[source_id] = Source(source_id, float(mjd), band)
    return sorted(found.values(), key=lambda source: (source.mjd, source.source_id))


def fetch_history(object_id: str, client: FinkClient) -> tuple[str, list[Source]]:
    """Try DIA first, then resolve a true SS object to its designation."""
    object_id = identifier(object_id, "objectid")
    dia = client.post_json("/api/v1/sources", {
        "diaObjectId": object_id,
        "columns": "r:diaObjectId,r:diaSourceId,r:midpointMjdTai,r:band",
        "output-format": "json",
    })
    if not isinstance(dia, list):
        raise ValueError("Malformed DIA source response")
    if dia:
        return "dia", _sources(dia, object_id, "r:diaObjectId")
    resolved = client.post_json("/api/v1/resolver", {
        "resolver": "ssodnet", "name_or_id": object_id, "reverse": True,
        "nmax": 10, "output-format": "json",
    })
    if not isinstance(resolved, list) or len(resolved) != 1 or not isinstance(resolved[0], dict):
        raise ValueError("Unknown object or ambiguous Solar-System designation")
    if identifier(resolved[0].get("r:ssObjectId"), "ssObjectId") != object_id:
        raise ValueError("Resolver identity does not match the requested object")
    designation = resolved[0].get("r:packed_primary_provisional_designation")
    if not isinstance(designation, str) or not re.fullmatch(r"[A-Za-z0-9]{1,32}", designation):
        raise ValueError("Invalid Solar-System designation")
    ss = client.post_json("/api/v1/sso", {
        "n_or_d": designation,
        "columns": "r:ssObjectId,r:diaSourceId,r:midpointMjdTai,r:band",
        "output-format": "json",
    })
    return "ss", _sources(ss, object_id, "r:ssObjectId")


def select_frames(sources: list[Source], maximum: int | None) -> list[Source]:
    if not sources:
        raise ValueError("No sources to animate")
    if maximum is None or maximum >= len(sources):
        return sources
    if maximum < 2:
        raise ValueError("--max-frames must be at least 2 to retain both history endpoints")
    return [sources[i * (len(sources) - 1) // (maximum - 1)] for i in range(maximum)]


def _decode_cutout(png: bytes, source: Source):
    try:
        from PIL import Image
    except ImportError as exc:
        raise RuntimeError("Pillow is required: python -m pip install Pillow") from exc
    with Image.open(io.BytesIO(png)) as cutout:
        if cutout.format != "PNG" or cutout.width < 1 or cutout.height < 1 or max(cutout.size) > 128:
            raise ValueError(f"Invalid PNG cutout for source {source.source_id}")
        return cutout.convert("RGB")


def _frame(tile, source: Source, scale: int, canvas_size: tuple[int, int]):
    from PIL import Image, ImageDraw
    width, height = canvas_size
    canvas = Image.new("RGB", (width, height * scale + 36), "black")
    canvas.paste(tile.resize((tile.width * scale, tile.height * scale), Image.Resampling.NEAREST),
                 ((width - tile.width * scale) // 2, (height - tile.height) * scale // 2))
    label = ImageDraw.Draw(canvas)
    label.text((4, height * scale + 2), f"MJD {source.mjd:.5f}  {source.band}", fill="white")
    label.text((4, height * scale + 17), f"source {source.source_id}", fill="white")
    return canvas


def write_gif(sources: list[Source], output: Path, client: FinkClient,
              kind: str, duration_ms: int, scale: int, workers: int = 1) -> None:
    """Fetch selected cutouts and publish a valid GIF only after every frame succeeds."""
    if not 20 <= duration_ms <= 10000 or not 1 <= scale <= 16 or not 1 <= workers <= 4:
        raise ValueError("Invalid duration, scale, or worker count")
    if len(sources) >= MAX_SOURCES or not sources:
        raise ValueError("Invalid animation frame count")
    output = Path(output)
    if not output.parent.is_dir():
        raise ValueError(f"Output directory does not exist: {output.parent}")
    try:
        from PIL import Image, ImageDraw
    except ImportError as exc:
        raise RuntimeError("Pillow is required: python -m pip install Pillow") from exc
    measure = ImageDraw.Draw(Image.new("RGB", (1, 1)))
    label_width = int(max(
        max(measure.textbbox((0, 0), f"MJD {source.mjd:.5f}  {source.band}")[2],
            measure.textbbox((0, 0), f"source {source.source_id}")[2])
        for source in sources
    )) + 8
    tiles = []
    max_width = max_height = 0
    with ThreadPoolExecutor(max_workers=workers) as executor:
        for offset in range(0, len(sources), workers):
            batch = sources[offset:offset + workers]
            futures = [executor.submit(client.cutout, source.source_id, kind) for source in batch]
            for source, future in zip(batch, futures):
                tile = _decode_cutout(future.result(), source)
                max_width = max(max_width, tile.width)
                max_height = max(max_height, tile.height)
                width = max(max_width * scale, label_width)
                projected = width * (max_height * scale + 36) * len(sources) * 3
                if projected > MAX_FRAME_RGB_BYTES:
                    raise ValueError("Animation exceeds memory budget; use --max-frames or --scale")
                tiles.append(tile)
    canvas_size = (max(max_width * scale, label_width), max_height)
    frames = [_frame(tile, source, scale, canvas_size) for source, tile in zip(sources, tiles)]
    tmp_name = None
    try:
        with tempfile.NamedTemporaryFile(prefix=f".{output.name}.", suffix=".gif",
                                         dir=output.parent, delete=False) as tmp:
            tmp_name = tmp.name
        frames[0].save(tmp_name, format="GIF", save_all=True, append_images=frames[1:],
                       duration=duration_ms, loop=0, disposal=2, optimize=False)
        os.replace(tmp_name, output)
    finally:
        if tmp_name and os.path.exists(tmp_name):
            os.unlink(tmp_name)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("objectid", help="numeric LSST DIA or SS object ID")
    parser.add_argument("--output", type=Path, help="GIF destination (default: OBJECTID-KIND.gif in current directory)")
    parser.add_argument("--kind", choices=("Difference", "Science", "Template"), default="Difference",
                        help="cutout image kind (default: Difference)")
    parser.add_argument("--max-frames", type=int, help="sample evenly over history; by default animate all sources")
    parser.add_argument("--duration-ms", type=int, default=200, help="duration per frame, 20–10000 ms (default: 200)")
    parser.add_argument("--scale", type=int, default=4, help="nearest-neighbor image enlargement, 1–16 (default: 4)")
    parser.add_argument("--workers", type=int, default=3, help="simultaneous cutout downloads, 1–4 (default: 3)")
    args = parser.parse_args(argv)
    try:
        object_id = identifier(args.objectid, "objectid")
        if args.max_frames is not None and args.max_frames < 2:
            raise ValueError("--max-frames must be at least 2")
        client = FinkClient()
        object_kind, sources = fetch_history(object_id, client)
        selected = select_frames(sources, args.max_frames)
        output = args.output or Path(f"{object_id}-{args.kind}.gif")
        print(f"{object_kind.upper()} {object_id}: {len(sources)} sources; downloading {len(selected)} {args.kind} cutouts", file=sys.stderr)
        write_gif(selected, output, client, args.kind, args.duration_ms, args.scale, args.workers)
        print(f"Created {output} ({len(selected)} chronological frames)")
        return 0
    except (ValueError, RuntimeError, OSError, json.JSONDecodeError) as exc:
        parser.exit(1, f"animate: {exc}\n")


if __name__ == "__main__":
    raise SystemExit(main())
