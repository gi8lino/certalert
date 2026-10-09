# CertAlert

[![tag](https://img.shields.io/github/tag/gi8lino/certalert.svg?style=flat-square)](https://github.com/gi8lino/certalert/releases/latest)
![tests](https://github.com/gi8lino/certalert/actions/workflows/tests.yml/badge.svg)
[![build](https://github.com/gi8lino/certalert/actions/workflows/release.yml/badge.svg)](https://github.com/gi8lino/certalert/actions/workflows/release.yml)
[![license](https://img.shields.io/github/license/gi8lino/certalert.svg?style=flat-square)](LICENSE)

CertAlert monitors configured X.509 certificates and Java keystores. It provides a dashboard at `/` and Prometheus metrics at `/metrics`.

## Use it

Create `certalert.yaml`:

```yaml
certalert:
  check-interval: 2m
  dashboard:
    warning-threshold: 20d
    critical-threshold: 3d
    zone-id: UTC
  certificates:
    - name: example
      path: /certs/example.pem
      type: pem
```

Run the container with the configuration and certificate files mounted read-only:

```bash
docker run --rm -p 8080:8080 \
  -v "$PWD/certalert.yaml:/config/certalert.yaml:ro" \
  -v "$PWD/certs:/certs:ro" \
  ghcr.io/gi8lino/certalert:latest
```

Open `http://localhost:8080` for the dashboard or scrape `http://localhost:8080/metrics` with Prometheus.

For Kubernetes, adapt the example files in `deploy/kubernetes` with your image, certificates, and secrets, then apply them:

```bash
kubectl apply -f deploy/kubernetes/
```

### Kubernetes certificate mounts

The configured certificate path must be the path **inside the container** and match the volume mount:

**certalert.yaml**

```yaml
certalert:
  certificates:
    - name: gateway
      path: /certs/gateway.p12
      type: pkcs12
```

Mount ConfigMaps and Secrets as complete directories, not with `subPath`. CertAlert reopens certificate files on every poll, so Kubernetes-projected file updates are used on the next scan after Kubernetes propagates them. Changes to `certalert.yaml` still require a Pod restart because application configuration is loaded at startup.

Supported source types are `pem`, `crt`, `jks`, `jceks`, `pkcs12`, `p12`, `dks`, and `pkcs11`.

## Password files

For keystores, use a Kubernetes Secret mounted at `/passwords` and refer to its in-container file path. The `//` separates the file path from the key to read:

```yaml
certalert:
  certificates:
    - name: gateway
      path: /certs/gateway.p12
      type: pkcs12
      password: file:/passwords/certificates.passwords//gateway_password
```

The mounted `certificates.passwords` file uses `key = value` lines:

```text
gateway_password = replace-me
```

Password-file updates are used on the next poll after Kubernetes projects the updated Secret. Other supported sources are `env:`, `json:`, `yaml:`, `ini:`, `properties:`, and `toml:`.

## Certificate states and alerts

CertAlert builds a new certificate snapshot on every polling interval.

- An expired but readable certificate remains `EXPIRED`, has validity metric value `1`, and remains visible in the dashboard.
- A renewed certificate updates its expiry and remaining-days metrics on the next scan.
- A removed configuration entry has its metrics removed on the next scan. Prometheus alerts based on those metrics resolve after the next scrape and rule evaluation; resolved notifications depend on Alertmanager receiver settings.
- A missing file, bad password, unreadable keystore, or certificate outside its validity period is `INVALID`.

The example [PrometheusRule](deploy/kubernetes/prometheus-rules.example.yaml) contains warning, urgent-expiry, expired, and invalid-certificate alerts.

## Metrics

- `certalert_certificate_expiration_seconds`: certificate expiry as a Unix timestamp.
- `certalert_certificate_days_remaining`: days until expiry.
- `certalert_certificate_validity`: `0` for valid and `1` for invalid or expired.

All metrics use the labels `certificate_name`, `alias`, `path`, and `type`.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](.github/CONTRIBUTING.md).

## License

Licensed under the [MIT License](LICENSE).
