#!/bin/bash

# This script creates a .desktop file so you can launch Relay Server
# directly from your application launcher (like rofi, wofi, GNOME overview, etc.)

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../LocalLinkWeb" && pwd)"
DESKTOP_DIR="$HOME/.local/share/applications"
DESKTOP_FILE="$DESKTOP_DIR/relay-server.desktop"

# Ensure the applications directory exists
mkdir -p "$DESKTOP_DIR"

cat <<EOF > "$DESKTOP_FILE"
[Desktop Entry]
Name=Relay Server
Comment=Buffered streaming, local-first file transfer
# Run the node server and keep the terminal open so you can see logs
Exec=sh -c "cd '$DIR' && npm start; exec bash"
Terminal=true
Type=Application
Icon=network-transmit-receive
Categories=Network;FileTransfer;
EOF

# Make the desktop file executable just in case
chmod +x "$DESKTOP_FILE"

echo "✅ Successfully installed shortcut to $DESKTOP_FILE"
echo "You can now launch 'Relay Server' from your application launcher."
