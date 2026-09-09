#!/usr/bin/env bash
# Компилирует все примеры *.java из папок API и запускает main() каждого.
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=verify/java/out
rm -rf "$OUT" && mkdir -p "$OUT"
javac --release 11 -encoding UTF-8 -Xlint:all -Werror -d "$OUT" $(find QalqanReceiveInfo QalqanReceiveInfoFromMIS -name "*.java")
status=0
for f in $(find QalqanReceiveInfo QalqanReceiveInfoFromMIS -name "*.java"); do
  cls=$(grep -m1 '^package ' "$f" | sed 's/package \(.*\);/\1/').$(basename "$f" .java)
  echo "===== $cls ====="
  java -cp "$OUT" "$cls" || status=1
  echo
done
exit $status
