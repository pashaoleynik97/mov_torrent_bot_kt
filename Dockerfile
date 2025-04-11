FROM openjdk:17-slim

# Install qBittorrent and tools
RUN apt-get update && apt-get install -y \
    qbittorrent-nox \
    git \
    curl \
    unzip \
    python3 \
    python3-pip \
    && apt-get clean

# Install yq (YAML parser)
RUN pip3 install yq

# Add user and setup working dir
RUN useradd -ms /bin/bash botuser
WORKDIR /home/botuser

# Copy entrypoint BEFORE switching user, so chmod works
COPY entrypoint.sh .

# Make it executable (must be done as root)
RUN chmod +x entrypoint.sh && chown botuser:botuser entrypoint.sh

# Switch to non-root user
USER botuser

ENTRYPOINT ["./entrypoint.sh"]
