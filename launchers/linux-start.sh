#!/bin/bash

# Move to the LocalLinkWeb directory relative to this script
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../LocalLinkWeb" && pwd)"
cd "$DIR" || exit 1

# Check if node_modules exists, install dependencies if not
if [ ! -d "node_modules" ]; then
    echo "Dependencies not found. Installing..."
    npm install
fi

echo "Starting Relay Web Server..."
npm start
