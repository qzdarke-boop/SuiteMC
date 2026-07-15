#!/bin/bash
set -e

cd "$(dirname "$0")"

./gradlew shadowJar

echo ""
echo "Build OK -> build/libs/psdk-1.0.0.jar"
