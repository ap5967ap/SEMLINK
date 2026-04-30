#!/bin/bash
# ──────────────────────────────────────────────────────────────────
# SEMLINK End-to-End Demo Script
# Run this from the repository root to execute the full pipeline
# and launch the integrated frontend.
# ──────────────────────────────────────────────────────────────────

set -e

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$REPO_ROOT"

# Use Java 21+ (adjust the version flag to match your system)
export JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || /usr/libexec/java_home -v 21 2>/dev/null || echo "$JAVA_HOME")

echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║           SEMLINK — End-to-End Demo Runner           ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
echo "Using JAVA_HOME=$JAVA_HOME"
echo ""

# ── Step 1: Compile ─────────────────────────────────────────────
echo "━━━ Step 1/6: Compiling Java backend ━━━"
mvn -q -DskipTests compile
echo "  ✓ Compilation successful"

# ── Step 2: Run the full pipeline ───────────────────────────────
echo ""
echo "━━━ Step 2/6: Running full AICTE semantic pipeline ━━━"
mvn -q exec:java -Dexec.args="pipeline run"
echo "  ✓ Pipeline artifacts generated in target/semantic-output/"

# ── Step 3: Run the wow query ───────────────────────────────────
echo ""
echo "━━━ Step 3/6: Running CS students query (wow moment) ━━━"
mvn -q exec:java -Dexec.args="query cs_students_by_university"

# ── Step 4: Run schema diff ────────────────────────────────────
echo ""
echo "━━━ Step 4/6: Running schema evolution diff ━━━"
mvn -q exec:java -Dexec.args="schema diff"

# ── Step 5: Install UI dependencies ────────────────────────────
echo ""
echo "━━━ Step 5/6: Installing frontend dependencies ━━━"
cd semlink-ui
npm install --silent 2>/dev/null
echo "  ✓ Dependencies installed"

# ── Step 6: Launch the frontend ────────────────────────────────
echo ""
echo "━━━ Step 6/6: Starting integrated frontend ━━━"
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  Open http://127.0.0.1:5173 in your browser         ║"
echo "║                                                      ║"
echo "║  The frontend reads live data from the pipeline      ║"
echo "║  output via /api/* routes.                           ║"
echo "║                                                      ║"
echo "║  Press Ctrl+C to stop.                               ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
npm run dev -- --port 5173
