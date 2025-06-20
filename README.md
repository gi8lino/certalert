# CertAlert

[![Release](https://img.shields.io/github/release/gi8lino/certalert.svg?style=flat-square)](https://github.com/gi8lino/certalert/releases/latest)
[![GitHub tag](https://img.shields.io/github/tag/gi8lino/certalert.svg?style=flat-square)](https://github.com/gi8lino/certalert/releases/latest)
![Tests](https://github.com/gi8lino/certalert/actions/workflows/tests.yml/badge.svg)
[![Build](https://github.com/gi8lino/certalert/actions/workflows/release.yml/badge.svg)](https://github.com/gi8lino/certalert/actions/workflows/release.yml)
[![license](https://img.shields.io/github/license/gi8lino/certalert.svg?style=flat-square)](LICENSE)

---

CertAlert is a Spring Boot application designed to solve the common problem of unnoticed SSL/TLS certificate expirations
and misconfigurations. It periodically scans configured certificates, classifies their status, and makes both a
human-friendly dashboard and Prometheus metrics available.

## Features

- Periodic certificate scanning for expiration and validity
- Dashboard view showing:
  - Not Before / Not After dates
  - Time until expiration (or expired)
  - Status classification (`VALID` / `INVALID` / `EXPIRED`)
- Configurable polling interval
- Prometheus metrics for expiration timestamps and validity state
- Flexible password resolution (literal, environment, file)

## Installation and Usage

### Kubernetes

Deploy manifests from `deploy/kubernetes`:

```bash
kubectl apply -f deploy/kubernetes/
```

- Mount your certificate secrets as files or Kubernetes `Secret`s.
- Provide `certalert.yaml` via a ConfigMap and mount it at `/config/certalert.yaml`.

## Configuration

```yaml
certalert:
  check-interval: 2m
  certificates: ...
  dashboard:
    warning-threshold: 20d
    critical-threshold: 3d
```

### Providing Credentials

You can specify credentials using a dynamic resolution scheme. The following formats are supported:

- **`env:`** – Resolves environment variables.
  Example:
  `env:PATH` → resolves to the value of the `PATH` environment variable.

- **`file:`** – Resolves values from plain text or key-value files.
  Example:
  `file:/config/app.txt//KeyName` → extracts `KeyName=value` from `app.txt`.
  `file:/config/token.txt` → returns the entire contents of `token.txt`.

- **`json:`** – Resolves values from JSON files using dot notation.
  Example:
  `json:/config/app.json//database.host` → resolves to `app.json → database → host`.
  `json:/config/app.json//servers.0.host` → supports array indexing.

- **`yaml:`** – Resolves values from YAML files using dot notation.
  Example:
  `yaml:/config/app.yaml//server.port` → resolves to `app.yaml → server → port`.
  `yaml:/config/app.yaml//servers.0.host` → supports array indexing.

- **`ini:`** – Resolves values from INI files using `Section.Key` format.
  Example:
  `ini:/config/app.ini//Database.Password` → resolves to `[Database] → Password`.

- **`properties:`** – Resolves values from `.properties` files using the key name.
  Example:
  `properties:/config/app.properties//db.user` → resolves to `db.user=value`.

- **`toml:`** – Resolves values from TOML files using dot notation.
  Example:
  `toml:/config/app.toml//database.host`
  `toml:/config/app.toml//servers.0.port`

- **No prefix** – Treated as a literal value and returned unchanged.
  Example:
  `my-secret-token-123` → returns as-is.

## Prometheus Metrics

CertAlert exposes a set of Prometheus metrics to monitor certificate expirations and validity. **Metrics are only
emitted after each polling interval for configured certificates**, so you may not see data immediately until the first
scan completes.

### Available Metrics

1. **`certalert_certificate_expiration_seconds`**

   - **Type:** Gauge
   - **Description:** Unix epoch timestamp (seconds) when the certificate expires (`Not After`).
   - **Labels:** `certificate_name`, `alias`

2. **`certalert_certificate_validity`**
   - **Type:** Gauge
   - **Description:** Certificate validity state (0 = valid, 1 = invalid or expired).
   - **Labels:** `certificate_name`, `alias`

Metrics are scraped at `/metrics` (or `/metrics` when using path mapping).

## Contributing

We welcome contributions of all kinds! Please see our [CONTRIBUTING.md](.github/CONTRIBUTING.md) for guidelines.

Key steps:

1. Fork the repository
2. Create a feature branch
3. Run tests and verify
4. Submit a Pull Request with clear description

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
