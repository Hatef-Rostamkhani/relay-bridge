# Docker Guide

Use Docker when you want the proxy isolated from your system Python environment.

## Requirements

- Docker
- Docker Compose
- A completed `config.json`

## Setup

Create `config.json` first:

```bash
cp config.example.json config.json
```

Edit `config.json` and set at least:

```json
{
  "script_id": "YOUR_APPS_SCRIPT_DEPLOYMENT_ID",
  "auth_key": "THE_SAME_SECRET_AS_CODE_GS"
}
```

Then start the container:

```bash
docker compose up -d
```

## GitHub Container Image

Pushes to any branch, and version tags such as `v1.2.0`, build and store a multi-architecture Docker image in GitHub Container Registry:

```text
ghcr.io/hatef-rostamkhani/relaybridge
```

Useful tags:

| Tag | Meaning |
|-----|---------|
| `latest` | Latest build from `main`, `master`, or a version tag |
| branch name | Latest build from that branch |
| `sha-<commit>` | Exact commit image |
| `v1.2.0` | Git tag image |
| `1.2.0`, `1.2` | Semver tags created from `v1.2.0` |

Pull it with:

```bash
docker pull ghcr.io/hatef-rostamkhani/relaybridge:latest
```

## Download Image Without GHCR

If `docker pull` from GHCR is blocked on your network, download the Docker image archive from the GitHub Release assets instead.

Stable release download URL format:

```text
https://github.com/Hatef-Rostamkhani/relay-bridge/releases/download/vX.Y.Z/RelayBridge-docker-vX.Y.Z-linux-amd64.tar.gz
```

Choose the file for your CPU architecture:

| File | Use |
|------|-----|
| `RelayBridge-docker-vX.Y.Z-linux-amd64.tar.gz` | Most Intel/AMD PCs and VPS servers |
| `RelayBridge-docker-vX.Y.Z-linux-arm64.tar.gz` | ARM64 servers, Apple Silicon Linux VMs, and many ARM devices |

Verify and load the image:

```bash
sha256sum -c RelayBridge-docker-vX.Y.Z-linux-amd64.tar.gz.sha256
docker load -i RelayBridge-docker-vX.Y.Z-linux-amd64.tar.gz
```

Run the loaded image:

```bash
docker run -d --name relaybridge --restart unless-stopped \
  -p 8085:8085 -p 1080:1080 \
  -v "$PWD/config.json:/app/config.json:ro" \
  -v "$PWD/ca:/app/ca" \
  relaybridge:vX.Y.Z
```

For branch builds, GitHub Actions also uploads temporary Docker archive artifacts named like `docker-image-sha-<commit>-linux-amd64`. GitHub may wrap those artifacts in a ZIP download; extract the ZIP first, then run `docker load -i` on the `.tar.gz` file inside it. Release assets are the recommended option for normal users because they are stable public downloads.

The compose file exposes:

| Port | Use |
|------|-----|
| `8085` | HTTP proxy |
| `1080` | SOCKS5 proxy |

Configure your browser to use HTTP proxy `127.0.0.1:8085`.

## Useful Commands

```bash
docker compose up -d          # Start in background
docker compose logs -f        # Follow logs
docker compose restart        # Restart after config changes
docker compose down           # Stop and remove container
docker compose build          # Rebuild after code changes
```

## Certificate Handling

The container writes the generated CA into `./ca` on your host because [docker-compose.yml](../docker-compose.yml) mounts that directory.

Install this file on the host, not inside the container:

```text
ca/ca.crt
```

Running `python main.py --install-cert` inside the container cannot update your host OS or browser trust store.

## Config And Secrets

[docker-compose.yml](../docker-compose.yml) mounts `config.json` read-only into the container. Your secrets stay on the host and are not baked into the image.

Do not commit or share your real `config.json`, `auth_key`, `ca/`, or exit-node PSK.
