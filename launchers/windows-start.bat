@echo off
TITLE Relay Web Server

:: Move to the RelayWeb directory relative to this script
cd /d "%~dp0..\RelayWeb"

:: Check if node_modules exists, install dependencies if not
IF NOT EXIST "node_modules" (
    echo Dependencies not found. Installing...
    npm install
)

echo Starting Relay Web Server...
npm start
pause
