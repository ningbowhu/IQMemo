#!/usr/bin/env python3
"""Formal release entry: requires current source-bound UI approval, no inherited waiver."""
import argparse
import sys
import ui_release_gate

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True)
    parser.add_argument("--version", required=True)
    args = parser.parse_args()
    sys.exit(ui_release_gate.main(["check", "--project", args.project, "--version", args.version]))
