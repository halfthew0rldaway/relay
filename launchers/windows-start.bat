@echo off
TITLE Relay Web Server

:: Move to the LocalLinkWeb directory relative to this script
cd /d "%~dp0..\LocalLinkWeb"

:: Check if node_modules exists, install dependencies if not
IF NOT EXIST "node_modules" (
    echo Dependencies not found. Installing...
    npm install
)

echo Starting Relay Web Server...
npm start
pause
