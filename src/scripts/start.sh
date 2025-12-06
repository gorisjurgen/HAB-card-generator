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

# Run the application with external config
echo "Starting HAB Card Generator..."
java -jar "$JAR_FILE" --spring.config.location="file:$CONFIG_DIR/application.yml"
