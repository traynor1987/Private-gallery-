# Nova Brain Cloud Teacher on James's VPS

The service uses an isolated Compose project, local port 8787 and a dedicated NGINX virtual host. Keep it loopback-only on the current Ubuntu 24.10 host. Before public activation, migrate to a supported Ubuntu LTS with security updates. Back up the VPS, Portainer state, NGINX configuration and any databases before migration. A side-by-side migration lets you verify the replacement before changing DNS.

## Loopback staging without OpenAI calls

From your own terminal, connect using your existing SSH access.

```sh
ssh ubuntu@79.137.32.206
sudo ss -ltn '( sport = :8787 )'
git clone --branch feature/novaos-sanctuary-integration https://github.com/traynor1987/Private-gallery-.git nova-cloud-source
cd nova-cloud-source/scripts/cloud_teacher
sudo install -d -m 0700 /etc/nova-cloud-teacher
sudo install -d -o 10001 -g 10001 -m 0700 /etc/nova-cloud-teacher/secrets
printf '%s\n' 'NOVA_CLOUD_TEACHER_MODEL=staging' | sudo tee /etc/nova-cloud-teacher/settings.env >/dev/null
sudo chmod 0600 /etc/nova-cloud-teacher/settings.env
openssl rand -hex 32 | sudo sh -c 'umask 077; cat > /etc/nova-cloud-teacher/secrets/owner-token; chown 10001:10001 /etc/nova-cloud-teacher/secrets/owner-token'
sudo bash deploy.sh
curl --fail --silent --show-error http://127.0.0.1:8787/health
```

Use the initial setup commands only for a fresh configuration. Keep an existing settings file and token during upgrades. Check that port 8787 is unused first. If you use another port, set `NOVA_HOST_PORT` for both deploy and rollback and change the upstream in the dedicated NGINX file. Use a current Docker Engine with loopback port publishing support. The image is tagged by repository commit. Keep the preceding image for rollback.

## HTTPS after the OS migration

Confirm the domain points to the supported host, including any AAAA record. Check `sudo nginx -T` for an existing server block for this domain. Use a new site file and preserve all existing sites.

1. Create `/var/www/nova-cloud-acme`. Add a temporary HTTP-only server block for `brain.jamestraynor.co.uk` in its own sites-available file. Use the port 80 block from `nginx-brain.conf`, with its catch-all location returning 404 until TLS is ready. Enable only that site and run `sudo nginx -t && sudo systemctl reload nginx`.
2. With Certbot installed on the supported host and port 80 reachable, run `sudo certbot certonly --webroot -w /var/www/nova-cloud-acme -d brain.jamestraynor.co.uk`.
3. Replace only the temporary site file with `nginx-brain.conf`. Run `sudo nginx -t && sudo systemctl reload nginx`. Confirm `curl --fail https://brain.jamestraynor.co.uk/health` works. Configure the Certbot deploy hook to reload NGINX after successful renewal and run `sudo certbot renew --dry-run`.

## Enable analysis only when authorised

Create `/etc/nova-cloud-teacher/secrets/openai-key` using a secure editor on the VPS. Set ownership to `10001:10001` and mode `0600`. Keep secrets outside the checkout. Configure a supported OpenAI model and set the following non-secret values in `settings.env`.

- `NOVA_CLOUD_CREDIT_CYCLE` is the unique funded cycle identifier. Keep it stable through redeployments.
- `NOVA_CLOUD_CREDIT_EXPIRY` is the actual expiry as Unix seconds.
- `NOVA_CLOUD_CONFIRMED_CREDIT_MICRO_USD` is the verified available credit in millionths of a dollar.
- `NOVA_CLOUD_MAX_OUTPUT_TOKENS` defaults to 1000 and accepts 16 through 128000 for GPT-5 mini. This limit includes reasoning tokens.
- `NOVA_CLOUD_REASONING_EFFORT` is optional. GPT-5 mini supports `minimal`, `low`, `medium`, and `high`. Omit the setting to use the model default. Try `low` for short advisory responses.
- `NOVA_CLOUD_MAX_REQUEST_MICRO_USD` must cover the configured request cost bound. At the documented standard GPT-5 mini rates this is `100000 + 2 × NOVA_CLOUD_MAX_OUTPUT_TOKENS` microdollars, charging for a full uncached context and the entire output allowance. Set it to at least `102000` at the default cap. Unsupported models or underfunded reservations fail closed. Review [model pricing](https://developers.openai.com/api/docs/models/gpt-5-mini) before changing models or enabling analysis. Reservations are retained on upstream failure because the provider may already have processed the request.
- `NOVA_CLOUD_LIMIT_MICRO_USD` is optional and can lower the default $90 ceiling.

Run `sudo bash deploy.sh` again. Test authenticated status first. A successful health check does not call OpenAI. Use only a synthetic message for the first approved analysis. The SQLite volume retains reservations across restarts. Never remove it during routine deployment or rollback. NGINX access logs and container logs are disabled for this service.

## Rollback

Run `sudo bash rollback.sh PREVIOUS_COMMIT_SHA` using the same config directory and host port. Use an image from this deployment series. Rollback keeps the SQLite volume and current configuration, including any existing spending reservations. To take the service offline, disable only its NGINX site, validate and reload NGINX, then stop only the Cloud Teacher Compose service.
