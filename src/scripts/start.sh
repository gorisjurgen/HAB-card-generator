#!/bin/bash

# Get the directory where this script is located
APP_HOME="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Find the JAR file
JAR_FILE=$(find "$APP_HOME" -maxdepth 1 -name "*.jar" -type f | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: No JAR file found in $APP_HOME"
    exit 1
fi

# Set the config directory
CONFIG_DIR="$APP_HOME/config"

# Number of label positions to skip on the first page (default 0)
SKIP_LABELS="${1:-0}"

# Run the application with external config
echo "Starting HAB Card Generator (skipping $SKIP_LABELS labels)..."
java -jar "$JAR_FILE" --spring.config.location="file:$CONFIG_DIR/application.yml" "$SKIP_LABELS"
