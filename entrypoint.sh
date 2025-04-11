#!/bin/bash
set -e

# Validate input arguments
if [[ "$1" != "--config" || -z "$2" || ! -f "$2" ]]; then
  echo "❌ Usage: docker run ... --config /config/config.yml"
  exit 1
fi

CONFIG_FILE="$2"
echo "📖 Loading config from $CONFIG_FILE"

# Export bot environment variables
export TG_BOT_TOKEN=$(yq '.bot."tg-bot-token"' "$CONFIG_FILE")
export TG_BOT_PASSWORD=$(yq '.bot."tg-bot-password"' "$CONFIG_FILE")

# Export download directories
export MOVIES_DIR=$(yq '.downloads."movies-dir"' "$CONFIG_FILE")
export SERIES_DIR=$(yq '.downloads."series-dir"' "$CONFIG_FILE")

# Export tracker credentials
TRACKERS=$(yq '.trackers | keys | .[]' "$CONFIG_FILE")
for tracker in $TRACKERS; do
  UPPER_NAME=$(echo "$tracker" | tr '[:lower:]' '[:upper:]')
  export "${UPPER_NAME}_USER"=$(yq ".trackers.$tracker.user" "$CONFIG_FILE")
  export "${UPPER_NAME}_PASS"=$(yq ".trackers.$tracker.password" "$CONFIG_FILE")
done

echo "✅ Environment variables loaded."

# Configure qBittorrent
echo "⚙️ Configuring qBittorrent..."

mkdir -p /home/botuser/.config/qBittorrent

cat > /home/botuser/.config/qBittorrent/qBittorrent.conf <<EOF
[AutoRun]
enabled=true
program=

[LegalNotice]
Accepted=true

[BitTorrent]
Session\\DefaultSavePath=$MOVIES_DIR
Session\\ScanDirs\\1\\Path=/queue/movies
Session\\ScanDirs\\1\\DownloadPath=$MOVIES_DIR
Session\\ScanDirs\\1\\Enabled=true
Session\\ScanDirs\\2\\Path=/queue/series
Session\\ScanDirs\\2\\DownloadPath=$SERIES_DIR
Session\\ScanDirs\\2\\Enabled=true

[Preferences]
General\\Locale=en
WebUI\\Enabled=true
WebUI\\Port=8080
WebUI\\Address=0.0.0.0
EOF

echo "✅ qBittorrent configured with scan and download paths."

# Start qBittorrent-nox
echo "🧲 Starting qBittorrent-nox..."
qbittorrent-nox &

# Wait for qBittorrent to start up
sleep 5

# Build and run bot
echo "📥 Cloning latest bot source..."
rm -rf bot-source
git clone https://github.com/pashaoleynik97/mov_torrent_bot_kt.git bot-source

echo "🛠️ Building Kotlin bot..."
cd bot-source
./gradlew shadowJar

echo "🚀 Launching bot..."
exec java -jar build/libs/*.jar