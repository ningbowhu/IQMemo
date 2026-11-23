#!/usr/bin/env python3
"""Read-only UI approval gate. Records evidence; does not authenticate human messages."""
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
from pathlib import Path
import struct
import sys


class GateError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise GateError(message)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def object_hash(value) -> str:
    return digest(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode())


def read_json(path: Path) -> dict:
    value = json.loads(path.read_text(encoding="utf-8"))
    require(isinstance(value, dict), f"JSON object required: {path}")
    return value


def inside(root: Path, relative: str) -> Path:
    require(isinstance(relative, str) and bool(relative.strip()), "nonempty relative path required")
    p = Path(relative)
    require(not p.is_absolute() and ".." not in p.parts and "\\" not in relative and ":" not in relative,
            f"unsafe relative path: {relative}")
    target = root / p
    require(target.resolve().is_relative_to(root.resolve()), f"path escapes project: {relative}")
    require(not any(x.is_symlink() for x in [target, *list(target.parents)[:len(p.parts)-1]]),
            f"symlink not accepted: {relative}")
    return target


def load_policy(root: Path) -> dict:
    policy = read_json(root / ".ui-review-policy.json")
    require(policy.get("schema_version") == 1, "unsupported UI policy schema")
    for key in ("source_inputs", "ui_inputs"):
        values = policy.get(key)
        require(isinstance(values, list) and bool(values) and all(isinstance(x, str) and x for x in values),
                f"policy {key} must be a nonempty list")
    require(isinstance(policy.get("exclude", []), list), "policy exclude must be a list")
    inside(root, policy.get("current_review", "界面截图/current.json"))
    return policy


def snapshot(root: Path, policy: dict) -> dict:
    files = {}
    for relative in policy["source_inputs"]:
        target = inside(root, relative)
        require(target.exists(), f"source input missing: {relative}")
        items = [target] if target.is_file() else sorted(target.rglob("*"))
        for path in items:
            rel = path.relative_to(root).as_posix()
            if any(fnmatch.fnmatchcase(rel, pat) for pat in policy.get("exclude", [])):
                continue
            require(not path.is_symlink(), f"source symlink not accepted: {rel}")
            if path.is_file():
                files[rel] = digest(path.read_bytes())
    require(bool(files), "source manifest is empty")
    payload = {"policy_sha256": object_hash(policy), "files": files}
    return {**payload, "sha256": object_hash(payload)}


def text_fields(value, fields, context):
    require(isinstance(value, dict), f"missing {context}")
    for field in fields:
        require(isinstance(value.get(field), str) and bool(value[field].strip()), f"missing {context}.{field}")


def validate_source(source):
    require(isinstance(source, dict) and isinstance(source.get("files"), dict) and bool(source["files"]),
            "source manifest required")
    require(source.get("sha256") == object_hash({k: source.get(k) for k in ("policy_sha256", "files")}),
            "source manifest digest mismatch")


def approval_subject(review: dict) -> str:
    return object_hash({k: review.get(k) for k in
                        ("schema_version", "version", "source", "environment", "screenshots", "shown_to_user")})


def validate_approved(root: Path, review: dict):
    require(review.get("schema_version") == 1 and review.get("state") == "approved", "UI approval pending, rejected or stale")
    text_fields(review, ("version",), "review")
    validate_source(review.get("source"))
    env = review.get("environment")
    text_fields(env, ("platform", "runtime", "viewport", "build_identity"), "environment")
    require(env.get("actual_running_ui") is True, "mockup cannot replace running UI")
    text_fields(review.get("shown_to_user"), ("message_ref", "shown_at"), "shown_to_user")
    shots = review.get("screenshots")
    require(isinstance(shots, list) and bool(shots), "screenshots required")
    seen = set()
    for shot in shots:
        text_fields(shot, ("path", "sha256", "screen", "state"), "screenshot")
        require(shot["path"] not in seen, "duplicate screenshot")
        seen.add(shot["path"])
        path = inside(root, shot["path"])
        require(path.parts[len(root.parts)] == "界面截图", "approval images must stay in project 界面截图 folder")
        data = path.read_bytes()
        require(data.startswith(b"\x89PNG\r\n\x1a\n") and data[12:16] == b"IHDR" and len(data) >= 33,
                "approval screenshot must be a PNG")
        width, height = struct.unpack(">II", data[16:24])
        require(width > 0 and height > 0, "invalid screenshot dimensions")
        require(digest(data) == shot["sha256"], f"screenshot changed: {shot['path']}")
    approval = review.get("approval")
    text_fields(approval, ("quote", "message_ref", "recorded_at", "subject_sha256"), "approval")
    require(approval.get("actor") == "user", "only actual user approval is accepted")
    require(approval["subject_sha256"] == approval_subject(review), "approval does not bind current evidence")


def check(root: Path, version: str) -> dict:
    root = root.resolve()
    policy = load_policy(root)
    current = snapshot(root, policy)
    pointer = read_json(inside(root, policy.get("current_review", "界面截图/current.json")))
    review_path = inside(root, pointer.get("review"))
    require(review_path.relative_to(root).parts[0] == "界面截图", "review must stay in 界面截图")
    review = read_json(review_path)
    require(review.get("schema_version") == 1, "unsupported review schema")
    require(review.get("version") == version, "review version differs from release version")
    validate_source(review.get("source"))
    require(review["source"] == current, "source or policy changed after review; recapture/reconfirm affected UI")
    if review.get("state") == "not_applicable":
        analysis = review.get("analysis")
        text_fields(analysis, ("reason",), "analysis")
        evidence = analysis.get("evidence")
        require(isinstance(evidence, list) and bool(evidence), "non-UI change analysis evidence required")
        for item in evidence:
            require(inside(root, item).is_file(), "non-UI analysis evidence missing")
        baseline = read_json(inside(root, review.get("baseline_review")))
        validate_approved(root, baseline)
        require(baseline["source"]["policy_sha256"] == current["policy_sha256"], "N/A baseline policy differs")
        old = baseline["source"]["files"]
        changed = [p for p in set(old) | set(current["files"]) if old.get(p) != current["files"].get(p)]
        require(not any(fnmatch.fnmatchcase(p, pat) for p in changed for pat in policy["ui_inputs"]),
                "N/A rejected: UI input changed")
        state = "NOT_APPLICABLE"
    else:
        validate_approved(root, review)
        state = "APPROVED"
    return {"passed": True, "state": state, "version": version,
            "review": review_path.relative_to(root).as_posix(), "source_sha256": current["sha256"],
            "limit": "Agent-maintained evidence; human message authenticity and semantic UI impact need review."}


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("action", choices=("snapshot", "subject", "check"))
    ap.add_argument("--project", required=True, type=Path)
    ap.add_argument("--version", help="Actual release version read by the packaging entry point")
    ap.add_argument("--review", help="Project-relative review path; subject action only")
    args = ap.parse_args(argv)
    try:
        root = args.project.resolve()
        if args.action == "snapshot":
            result = snapshot(root, load_policy(root))
        elif args.action == "subject":
            result = {"subject_sha256": approval_subject(read_json(inside(root, args.review)))}
        else:
            require(bool(args.version), "check requires --version")
            result = check(root, args.version)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0
    except (GateError, OSError, ValueError, TypeError, KeyError, struct.error) as exc:
        print(json.dumps({"passed": False, "state": "BLOCKED", "reason": str(exc)}, ensure_ascii=False), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
