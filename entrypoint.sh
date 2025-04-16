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
R_TG_BOT_TOKEN=$(yq '.bot."tg-bot-token"' "$CONFIG_FILE")
export TG_BOT_TOKEN=${R_TG_BOT_TOKEN//\"/}
R_TG_BOT_PASSWORD=$(yq '.bot."tg-bot-password"' "$CONFIG_FILE")
export TG_BOT_PASSWORD=${R_TG_BOT_PASSWORD//\"/}

# Export download directories
R_MOVIES_DIR=$(yq '.downloads."movies-dir"' "$CONFIG_FILE")
export MOVIES_DIR=${R_MOVIES_DIR//\"/}
R_SERIES_DIR=$(yq '.downloads."series-dir"' "$CONFIG_FILE")
export SERIES_DIR=${R_SERIES_DIR//\"/}

# Export tracker credentials
TRACKERS=$(yq '.trackers | keys | .[]' "$CONFIG_FILE")
for tracker in $TRACKERS; do
  CLEAN_NAME=$(echo "$tracker" | tr -d '"')
  UPPER_NAME=$(echo "$CLEAN_NAME" | tr '[:lower:]' '[:upper:]')
  TR_USR=${UPPER_NAME}_USER
  TR_PWD=${UPPER_NAME}_PASS

  export $TR_USR="$(yq -r ".trackers.$CLEAN_NAME.user" "$CONFIG_FILE")"
  export $TR_PWD="$(yq -r ".trackers.$CLEAN_NAME.password" "$CONFIG_FILE")"
  echo "→ Exported $TR_USR and $TR_PWD"
done

echo "✅ Environment variables loaded."

# Prepare torrent queue folders
echo "📁 Ensuring queue directories..."
mkdir -p /home/botuser/bot-source/queue/movie
mkdir -p /home/botuser/bot-source/queue/series

# Configure qBittorrent
echo "⚙️ Configuring qBittorrent..."

CONFIG_ROOT="/home/botuser/.config/qbt"
rm -rf "$CONFIG_ROOT"
mkdir -p "$CONFIG_ROOT"

CONFIG_DIR="/home/botuser/.config/qbt/qBittorrent/config"
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
DynDNS\\DomainName=changeme.dyndns.org
DynDNS\\Enabled=false
DynDNS\\Password=
DynDNS\\Service=0
DynDNS\\Username=
General\\Locale=
MailNotification\\email=
MailNotification\\enabled=false
MailNotification\\password=
MailNotification\\req_auth=true
MailNotification\\req_ssl=false
MailNotification\\sender=qBittorrent_notification@example.com
MailNotification\\smtp_server=smtp.changeme.com
MailNotification\\username=
Queueing\\QueueingEnabled=true
WebUI\\Address=*
WebUI\\AlternativeUIEnabled=false
WebUI\\AuthSubnetWhitelist=@Invalid()
WebUI\\AuthSubnetWhitelistEnabled=false
WebUI\\BanDuration=3600
WebUI\\CSRFProtection=true
WebUI\\ClickjackingProtection=true
WebUI\\CustomHTTPHeaders=
WebUI\\CustomHTTPHeadersEnabled=false
WebUI\\HTTPS\\CertificatePath=
WebUI\\HTTPS\\Enabled=false
WebUI\\HTTPS\\KeyPath=
WebUI\\HostHeaderValidation=true
WebUI\\LocalHostAuth=true
WebUI\\MaxAuthenticationFailCount=5
WebUI\\Port=8080
WebUI\\RootFolder=
WebUI\\SecureCookie=true
WebUI\\ServerDomains=*
WebUI\\SessionTimeout=3600
WebUI\\UseUPnP=true
WebUI\\Username=admin
EOF

# Ensure ownership and permissions
chown -R botuser:botuser /home/botuser/.config

echo "✅ qBittorrent configured with scan and download paths."

# Build and run bot
echo "📥 Cloning latest bot source..."
rm -rf bot-source
git clone https://github.com/pashaoleynik97/mov_torrent_bot_kt.git bot-source

echo "🛠️ Building Kotlin bot..."
cd bot-source
chmod +x gradlew
./gradlew shadowJar

echo "🚀 Launching bot..."
exec java -jar build/libs/*.jar

# Start qBittorrent-nox
echo "🧲 Starting qBittorrent-nox..."
qbittorrent-nox --profile=$CONFIG_ROOT &

# Wait for qBittorrent to start up
sleep 5
