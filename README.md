# 📦 movtorrentbot

Telegram bot that helps you download movies or TV series from torrent trackers. It runs alongside a qBittorrent client inside a single Docker container. Just drop torrent links or files — the bot does the rest.

## ✅ Prerequisites

1. Ubuntu host with Docker and Docker Compose.

2. Mounted TrueNAS share.

# Step-by-step guide

## 🧭 Overview

This guide will help you:

1. ✅ Create a Telegram bot via BotFather

2. ✅ Clone and configure the movtorrentbot project

3. ✅ Configure TrueNAS SMB share in Ubuntu

4. ✅ Set up Docker environment (Dockerfile, entrypoint, config, compose)

5. ✅ Deploy bot + qBittorrent

6. ✅ Automatically watch torrent folders and start downloads

7. ✅ Make it restart-safe (persistent config)

## 🧱 1. Create a Telegram Bot

1. Open `@BotFather` in Telegram

2. Type /start

3. Run /newbot and follow the prompts

4. Give it a name and a username (e.g., `movies_torrent_bot`)

5. You’ll receive a token like:

```
123456789:AAHx....yourTokenHere
```

Save that token — you'll place it in your config file later.

## 📁 2. Clone the Project

On your Ubuntu VM or local machine with Docker:

```bash
git clone https://github.com/pashaoleynik97/mov_torrent_bot_kt.git
cd mov_torrent_bot_kt
```

This repo will contain:

- Kotlin Telegram bot logic

- Dockerfile

- entrypoint.sh

- Docker Compose

- Config file

## 🔗 3. Mount Your TrueNAS SMB Share

Edit `/etc/fstab`:

```bash
sudo nano /etc/fstab
```

Add this line at the bottom:

```bash
# Jellyfin TrueNAS share
//192.168.0.XXX/smb-share /mnt/media cifs username=<smb_username>,password='<smb_password>',uid=1000,gid=1000,dir_mode=0775,file_mode=0664 0 0
```

Then mount:

```bash
sudo mount -a
```

Check access:

```bash
ls -la /mnt/media
```

## ⚙️ 4. Prepare Configuration Files

### 🔧 `config.yml`

```yaml
bot:
  tg-bot-token: "123456:ABC-your-bot-token"
  tg-bot-password: "letmein"

trackers:
  mazepa:
    user: "mazepa_username"
    password: "mazepa_password"

downloads:
  movies-dir: "/downloads/movies"
  series-dir: "/downloads/series"
```

💡 These paths will be mounted from your host, and accessible inside Docker.

### 🧩 docker-compose.yml

Check `/downloads/movies` and `/downloads/series` aliases (maybe you've got another naming inside SMB shares)

```yaml
version: "3.9"

services:
  movtorrentbot:
    build: .
    container_name: movtorrentbot
    volumes:
      - ./config.yml:/config.yml:ro
      - ./queue:/queue
      - /mnt/media/Movies:/downloads/movies:rw
      - /mnt/media/Shows:/downloads/series:rw
    command: ["--config", "/config.yml"]
    restart: unless-stopped
    environment:
      JAVA_OPTS: "-Xmx512m"
    ports:
      - "8080:8080"  # qBittorrent Web UI
```

## 🚀 5. Build & Run It

From the root of your project:

```bash
docker-compose up --build
```

Check logs for:

- ✅ Bot welcome message

- ✅ qBittorrent startup

- ✅ Tracker login success

- ✅ Torrent folder scan setup

## 🧪 6. Test the Flow

1. Message your bot in Telegram

2. Type /start and authenticate

3. Try /menu and send a movie name

4. Select a tracker

5. Choose a release

6. Confirm download

7. 🎉 Check if qBittorrent starts downloading