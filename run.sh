#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Multi-Agent ADD HPS — one-button runner.
#
# What this script does:
#   1. Checks pre-requisites (JDK 17, Maven). Prints install hints if missing.
#   2. Validates application.properties has an API key.
#   3. Warns if architecture_report.md already exists (so you don't overwrite history).
#   4. Compiles with maven and runs the multi-agent workflow with auto-approve.
#   5. Prints output file sizes when done.
#
# Usage:
#   bash run.sh                     # auto-approve (default) — non-interactive
#   bash run.sh --interactive       # ask approve/retry at each checkpoint
#   bash run.sh --max-revisions=3   # raise critic revision cap (default 2)
# -----------------------------------------------------------------------------
set -euo pipefail

# Normalize to project root.
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_DIR"

# -- 1. Pre-requisites ---------------------------------------------------------

need() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "❌ Missing required command: $1"
        echo "   $2"
        return 1
    fi
}

MISSING=0
if ! need java 'Install JDK 17:  brew install --cask temurin@17    (then re-open the terminal)'; then MISSING=1; fi
if ! need mvn  'Install Maven:   brew install maven'; then MISSING=1; fi
if [ "$MISSING" -ne 0 ]; then
    echo
    echo "Install the missing tools above, then re-run:  bash run.sh"
    exit 1
fi

JAVA_VERSION="$(java -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+)\..*/\1/')"
if [ "${JAVA_VERSION:-0}" -lt 17 ]; then
    echo "❌ JDK 17 or newer required, but detected: $(java -version 2>&1 | head -1)"
    echo "   Install JDK 17:  brew install --cask temurin@17"
    exit 1
fi

# -- 2. API key check ---------------------------------------------------------

APP_PROPS="src/main/resources/application.properties"
if [ ! -f "$APP_PROPS" ]; then
    echo "❌ Missing $APP_PROPS"
    echo "   Copy the example and fill in your Qwen API key:"
    echo "     cp src/main/resources/application.properties.example $APP_PROPS"
    echo "     open -e $APP_PROPS    # then paste your spring.ai.openai.api-key"
    exit 1
fi
if ! grep -E '^\s*spring\.ai\.openai\.api-key=\S+' "$APP_PROPS" >/dev/null; then
    echo "❌ $APP_PROPS has no api-key value."
    echo "   Open it and set:  spring.ai.openai.api-key=sk-..."
    exit 1
fi

# -- 3. Don't overwrite history by accident -----------------------------------

if [ -f architecture_report.md ] || [ -f conversation_log.md ]; then
    echo "⚠️  architecture_report.md / conversation_log.md already exists in the working dir."
    echo "   They will be OVERWRITTEN by this run."
    echo "   To keep them, ctrl-C now and run:  mkdir -p archive && mv architecture_report.md conversation_log.md archive/"
    echo "   Continuing in 4 seconds..."
    sleep 4
fi

# -- 4. Parse CLI flags ------------------------------------------------------

AUTO_APPROVE="true"
MAX_REVISIONS=""
for arg in "$@"; do
    case "$arg" in
        --interactive) AUTO_APPROVE="false" ;;
        --auto-approve=*) AUTO_APPROVE="${arg#*=}" ;;
        --max-revisions=*) MAX_REVISIONS="${arg#*=}" ;;
        *) echo "Unknown flag: $arg" ; exit 1 ;;
    esac
done

JAVA_OPTS=( "-Dma.auto-approve=$AUTO_APPROVE" )
if [ -n "$MAX_REVISIONS" ]; then
    JAVA_OPTS+=( "-Dma.max-revisions=$MAX_REVISIONS" )
fi
JAVA_OPTS_STR="${JAVA_OPTS[*]}"

# -- 5. Compile + run ---------------------------------------------------------

echo "▶ Compiling..."
mvn -q -DskipTests compile

echo "▶ Running multi-agent workflow (auto-approve=$AUTO_APPROVE, max-revisions=${MAX_REVISIONS:-default})"
echo "  Logs are streamed below. This will take ~5-10 minutes on Qwen3-32B."
echo

set +e
MAVEN_OPTS="$JAVA_OPTS_STR" mvn -q exec:java -Dexec.mainClass=com.hotel.system.App
RUN_EXIT=$?
set -e

# -- 6. Summary ---------------------------------------------------------------

echo
if [ $RUN_EXIT -ne 0 ]; then
    echo "❌ Run failed (exit code $RUN_EXIT). See errors above."
    exit $RUN_EXIT
fi

echo "✅ Done. Outputs:"
for f in conversation_log.md architecture_report.md; do
    if [ -f "$f" ]; then
        lines=$(wc -l < "$f" | tr -d ' ')
        bytes=$(wc -c < "$f" | tr -d ' ')
        echo "   - $PWD/$f   ($lines lines, $bytes bytes)"
    fi
done

echo
echo "Next step:"
echo "  1. Open architecture_report.md to check the design quality."
echo "  2. If it looks good, fill in Section 三 (Individual Reflection + contributions table) by hand."
echo "  3. Generate a PDF (requires pandoc + xelatex installed):"
echo "       pandoc architecture_report.md \\"
echo "         --from gfm --to pdf --pdf-engine=xelatex \\"
echo "         -V CJKmainfont=\"PingFang SC\" -V geometry:margin=2cm \\"
echo "         --toc --number-sections -o final_report.pdf"
