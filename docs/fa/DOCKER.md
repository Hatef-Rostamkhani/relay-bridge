# راهنمای Docker

Docker زمانی مفید است که می‌خواهید پروژه را بدون نصب مستقیم Python اجرا کنید.

## پیش‌نیاز

- Docker یا Docker Desktop
- فایل `config.json` آماده
- رله Apps Script که از [apps_script/Code.gs](../../apps_script/Code.gs) deploy شده باشد

## اجرای سریع

در پوشه پروژه اجرا کنید:

```bash
docker compose up --build
```

پورت‌های پیش‌فرض:

| سرویس | آدرس |
|-------|------|
| HTTP proxy | `127.0.0.1:8085` |
| SOCKS5 proxy | `127.0.0.1:1080` |

## تنظیم مرورگر

مرورگر را روی HTTP proxy با آدرس `127.0.0.1` و پورت `8085` تنظیم کنید.

اگر از HTTPS استفاده می‌کنید، باید گواهی ساخته‌شده در `ca/ca.crt` را روی سیستم یا مرورگر trust کنید.

## دانلود Image بدون GHCR

اگر `docker pull` از GHCR روی شبکه شما خطا می‌دهد، می‌توانید فایل Docker image را از GitHub Release دانلود کنید.

قالب لینک پایدار Release به این شکل است:

```text
https://github.com/Hatef-Rostamkhani/relay-bridge/releases/download/vX.Y.Z/RelayBridge-docker-vX.Y.Z-linux-amd64.tar.gz
```

فایل مناسب معماری سیستم را انتخاب کنید:

| فایل | کاربرد |
|------|--------|
| `RelayBridge-docker-vX.Y.Z-linux-amd64.tar.gz` | بیشتر PCها، لپ‌تاپ‌ها، و VPSهای Intel/AMD |
| `RelayBridge-docker-vX.Y.Z-linux-arm64.tar.gz` | سرورهای ARM64 و دستگاه‌های ARM |

فایل را بررسی و load کنید:

```bash
sha256sum -c RelayBridge-docker-vX.Y.Z-linux-amd64.tar.gz.sha256
docker load -i RelayBridge-docker-vX.Y.Z-linux-amd64.tar.gz
```

سپس image را اجرا کنید:

```bash
docker run -d --name relaybridge --restart unless-stopped \
  -p 8085:8085 -p 1080:1080 \
  -v "$PWD/config.json:/app/config.json:ro" \
  -v "$PWD/ca:/app/ca" \
  relaybridge:vX.Y.Z
```

برای buildهای branch، GitHub Actions یک artifact موقت با نامی شبیه `docker-image-sha-<commit>-linux-amd64` می‌سازد. ممکن است GitHub این artifact را داخل ZIP دانلود کند؛ ابتدا ZIP را extract کنید و بعد روی فایل `.tar.gz` داخل آن `docker load -i` بزنید. برای کاربران نهایی، فایل‌های GitHub Release گزینه پایدارتر و پیشنهادی هستند.

## توقف

```bash
docker compose down
```

## نکته‌ها

- مقدارهای محرمانه مثل `auth_key` را داخل تصویر Docker منتشر نکنید.
- اگر `config.json` را تغییر دادید، container را restart کنید.
- اگر پورت‌ها اشغال هستند، پورت‌های `docker-compose.yml` را تغییر دهید.

برای تنظیمات کامل‌تر، [مرجع تنظیمات](CONFIGURATION.md) را بخوانید.
