#!/bin/sh
# Compiles and runs the tests of the Android-independent logic with the plain JDK.
set -e
cd "$(dirname "$0")"
OUT=build/logic-tests
SRC=app/src/main/java/com/github/ulresh/mental_arithmetic
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
    "$SRC/AnswerParser.java" "$SRC/Problem.java" "$SRC/Trainer.java" \
    tests/java/com/github/ulresh/mental_arithmetic/LogicTests.java
java -cp "$OUT" com.github.ulresh.mental_arithmetic.LogicTests
