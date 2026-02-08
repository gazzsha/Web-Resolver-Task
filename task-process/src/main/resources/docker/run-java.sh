#!/bin/sh
CLASS_NAME=$1
INPUT_FILE=$2
EXPECTED_FILE=$3

javac "$CLASS_NAME.java"
if [ $? -ne 0 ]; then
  echo "Compilation Error"
  exit 1
fi

OUTPUT=$(timeout 2 java "$CLASS_NAME" < "$INPUT_FILE" 2>&1)
STATUS=$?

if [ $STATUS -ne 0 ]; then
  echo "Runtime Error"
  echo "$OUTPUT"
  exit 1
fi

if [ "$OUTPUT" = "$(cat "$EXPECTED_FILE")" ]; then
  echo "OK"
else
  echo "WA"
fi
