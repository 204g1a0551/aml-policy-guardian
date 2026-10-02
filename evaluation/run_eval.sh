#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "Starting AML Policy Guardian RAG Evaluation Harness..."
cd "$PROJECT_ROOT"
python3 evaluation/run_eval.py "$@"
