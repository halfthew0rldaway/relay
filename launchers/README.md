# Relay Launchers

This folder contains helper scripts to quickly run or install the Relay Web Server without needing to manually open a terminal and navigate to the folder every time.

## Linux (Fedora / Ubuntu / Arch / Hyprland / etc.)

### Option 1: App Launcher Shortcut (Recommended)
If you use a desktop environment or a launcher like `wofi`, `rofi`, or GNOME:
1. Run `./install-linux-shortcut.sh` once.
2. You can now search for **"Relay Server"** in your application launcher and start it like a normal app. It will open a terminal window automatically so you can see the logs and close it when you're done.

### Option 2: Direct Script
Just double-click or run `./linux-start.sh` from the terminal.

## Windows

Just double-click `windows-start.bat`. 
It will automatically install any missing dependencies and start the server. A terminal window will open, and you can close the window when you want to stop the server.
