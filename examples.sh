#!/bin/bash
# ──────────────────────────────────────────────────────────────────
# SEMLINK Examples — Individual demo commands to showcase features
# Run from the repository root.
# ──────────────────────────────────────────────────────────────────

set -e
cd "$(dirname "$0")"

case "${1:-help}" in

  compile)
    echo "▸ Compiling backend..."
    mvn -q -DskipTests compile
    echo "  ✓ Done"
    ;;

  pipeline)
    echo "▸ Running full AICTE pipeline (merge + reason + validate + query + report)..."
    mvn -q exec:java -Dexec.args="pipeline run"
    echo "  ✓ Output: target/semantic-output/"
    ;;

  query-wow)
    echo "▸ CS students by university (the wow query)..."
    mvn -q exec:java -Dexec.args="query cs_students_by_university"
    ;;

  query-dedup)
    echo "▸ Cross-university student deduplication..."
    mvn -q exec:java -Dexec.args="query same_as_student_details"
    ;;

  query-all)
    echo "▸ All students across universities..."
    mvn -q exec:java -Dexec.args="query all_students"
    ;;

  validate)
    echo "▸ Running SHACL validation..."
    mvn -q exec:java -Dexec.args="validate"
    ;;

  schema-diff)
    echo "▸ Running schema evolution diff..."
    mvn -q exec:java -Dexec.args="schema diff"
    ;;

  nl)
    shift
    QUESTION="${*:-Show students with CGPA above 9 from all universities}"
    echo "▸ Natural language query: $QUESTION"
    mvn -q exec:java -Dexec.args="nl $QUESTION"
    ;;

  r2o-raw)
    echo "▸ R2O raw export (SQL → RDF)..."
    mvn -q exec:java -Dexec.args="r2o raw example-college"
    ;;

  r2o-assist)
    echo "▸ R2O assisted alignment..."
    mvn -q exec:java -Dexec.args="r2o assist example-college"
    ;;

  usecases)
    echo "▸ Listing available use-case scenarios..."
    mvn -q exec:java -Dexec.args="usecase list"
    ;;

  usecase)
    ID="${2:-usecase1}"
    echo "▸ Running use case: $ID"
    mvn -q exec:java -Dexec.args="usecase run $ID"
    ;;

  connect-add)
    echo "▸ Adding university1 as OWL connection..."
    mvn -q exec:java -Dexec.args="connect add --type owl --id university1 --path src/main/resources/semantic/ontologies/local/university1/university1.ttl"
    ;;

  connect-list)
    echo "▸ Listing registered connections..."
    mvn -q exec:java -Dexec.args="connect list"
    ;;

  ui)
    echo "▸ Starting integrated frontend..."
    cd semlink-ui
    npm install --silent 2>/dev/null
    npm run dev -- --port 5173
    ;;

  full-demo)
    echo "▸ Running end-to-end demo..."
    bash run-demo.sh
    ;;

  help|*)
    echo ""
    echo "SEMLINK Examples"
    echo "════════════════"
    echo ""
    echo "Usage: ./examples.sh <command>"
    echo ""
    echo "  compile       Compile the Java backend"
    echo "  pipeline      Run the full AICTE pipeline"
    echo "  query-wow     CS students by university (wow query)"
    echo "  query-dedup   Cross-university deduplication"
    echo "  query-all     All students across universities"
    echo "  validate      SHACL validation"
    echo "  schema-diff   Schema evolution detection"
    echo "  nl [question] Natural-language query"
    echo "  r2o-raw       SQL-to-RDF raw export"
    echo "  r2o-assist    Assisted alignment"
    echo "  usecases      List use-case scenarios"
    echo "  usecase [id]  Run a specific use case"
    echo "  connect-add   Register a source connection"
    echo "  connect-list  List connections"
    echo "  ui            Start the integrated frontend"
    echo "  full-demo     Run entire end-to-end demo"
    echo ""
    ;;
esac
