#!/bin/bash
set -e

# === Step 1: Parse arguments ===
if [[ "$1" != "--config" || -z "$2" || ! -f "$2" ]]; then
  echo "❌ Usage: docker run ... --config /config/config.yml"
  exit 1
fi

CONFIG_FILE="$2"
echo "📖 Loading config from $CONFIG_FILE"

# === Step 2: Export env variables ===
export TG_BOT_TOKEN=$(yq -r '.bot."tg-bot-token"' "$CONFIG_FILE")
export TG_BOT_PASSWORD=$(yq -r '.bot."tg-bot-password"' "$CONFIG_FILE")
export MOVIES_DIR=$(yq -r '.downloads."movies-dir"' "$CONFIG_FILE")
export SERIES_DIR=$(yq -r '.downloads."series-dir"' "$CONFIG_FILE")

TRACKERS=$(yq -r '.trackers | keys[]' "$CONFIG_FILE")
for tracker in $TRACKERS; do
  NAME_UPPER=$(echo "$tracker" | tr '[:lower:]' '[:upper:]')
  export "${NAME_UPPER}_USER"=$(yq -r ".trackers.\"$tracker\".user" "$CONFIG_FILE")
  export "${NAME_UPPER}_PASS"=$(yq -r ".trackers.\"$tracker\".password" "$CONFIG_FILE")
  echo "→ Exported ${NAME_UPPER}_USER and ${NAME_UPPER}_PASS"
done

echo "✅ Environment variables loaded."

# === Step 3: Prepare queue directories ===
echo "📁 Ensuring queue directories..."
mkdir -p /home/botuser/bot-source/queue/movie
mkdir -p /home/botuser/bot-source/queue/series

# === Step 4: Configure qBittorrent ===
echo "⚙️ Configuring qBittorrent..."
CONFIG_ROOT="/home/botuser/.config/qbt"
CONFIG_DIR="$CONFIG_ROOT/qBittorrent/config"
rm -rf "$CONFIG_ROOT"
mkdir -p "$CONFIG_DIR"

cat > "$CONFIG_DIR/qBittorrent.conf" <<EOF
[AutoRun]
enabled=true
program=

[BitTorrent]
Session\\DisableAutoTMMByDefault=false
Session\\DisableAutoTMMTriggers\\CategoryChanged=true

[Preferences]
Advanced\\RecheckOnCompletion=false
Advanced\\trackerPort=9000
Connection\\PortRangeMin=26636
Connection\\ResolvePeerCountries=true
Downloads\\SavePath=/downloads/
Downloads\\ScanDirsV2=@Variant(\\0\\0\\0\\x1c\\0\\0\\0\\x2\\0\\0\\0H\\0/\\0h\\0o\\0m\\0e\\0/\\0b\\0o\\0t\\0u\\0s\\0e\\0r\\0/\\0b\\0o\\0t\\0-\\0s\\0o\\0u\\0r\\0c\\0e\\0/\\0q\\0u\\0e\\0u\\0e\\0/\\0m\\0o\\0v\\0i\\0e\\0\\0\\0\\n\\0\\0\\0\\"\\0/\\0d\\0o\\0w\\0n\\0l\\0o\\0a\\0d\\0s\\0/\\0m\\0o\\0v\\0i\\0e\\0s\\0\\0\\0J\\0/\\0h\\0o\\0m\\0e\\0/\\0b\\0o\\0t\\0u\\0s\\0e\\0r\\0/\\0b\\0o\\0t\\0-\\0s\\0o\\0u\\0r\\0c\\0e\\0/\\0q\\0u\\0e\\0u\\0e\\0/\\0s\\0e\\0r\\0i\\0e\\0s\\0\\0\\0\\n\\0\\0\\0\\"\\0/\\0d\\0o\\0w\\0n\\0l\\0o\\0a\\0d\\0s\\0/\\0s\\0e\\0r\\0i\\0e\\0s)
WebUI\\Address=*
WebUI\\Port=8080
WebUI\\Username=admin
WebUI\\LocalHostAuth=true
WebUI\\SecureCookie=true
EOF

chown -R botuser:botuser "$CONFIG_ROOT"
echo "✅ qBittorrent config ready."

# === Step 5: Build and start the bot ===
echo "📥 Cloning latest bot source..."
rm -rf bot-source
git clone https://github.com/pashaoleynik97/mov_torrent_bot_kt.git bot-source

echo "🚀 Launching bot..."
cd /home/botuser/bot-source
chmod +x gradlew
./gradlew shadowJar
java -jar build/libs/*.jar &

# === Step 6: Start qBittorrent and restart once ===
echo "🧲 Starting qBittorrent..."
qbittorrent-nox --profile="$CONFIG_ROOT" &
QBT_PID=$!

echo "⏳ Waiting for qBittorrent to initialize..."
sleep 10

echo "🔄 Restarting qBittorrent to ensure config is loaded..."
kill "$QBT_PID"
sleep 5

qbittorrent-nox --profile="$CONFIG_ROOT"
