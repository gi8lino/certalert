# CertAlert

[![GitHub tag](https://img.shields.io/github/tag/gi8lino/certalert.svg?style=flat-square)](https://github.com/gi8lino/certalert/releases/latest)
![Tests](https://github.com/gi8lino/certalert/actions/workflows/tests.yml/badge.svg)
[![Build](https://github.com/gi8lino/certalert/actions/workflows/release.yml/badge.svg)](https://github.com/gi8lino/certalert/actions/workflows/release.yml)
[![license](https://img.shields.io/github/license/gi8lino/certalert.svg?style=flat-square)](LICENSE)

---

**CertAlert** is a Spring Boot application that solves the all-too-common problem of unnoticed SSL/TLS certificate expirations and misconfigurations. It periodically scans your configured certificates, classifies their status, and makes both a human-friendly dashboard and Prometheus metrics available.

## ✨ Features

- Periodic certificate scanning for expiration and validity
- Dashboard view showing:

  - `Not Before` / `Not After` dates
  - Time until expiration (or since expiration)
  - Status classification (`VALID`, `INVALID`, `EXPIRED`)

- Configurable polling interval
- Prometheus metrics for expiration timestamps and validity state
- Flexible password resolution (literal, env, file, and more)

### Supported Certificate Types

CertAlert supports a variety of common certificate formats, including both keystore-based and plain certificate files:

#### Keystore Formats

These are loaded using Java's `KeyStore` API:

- **JKS** – Java KeyStore (`.jks`)
- **JCEKS** – Java Cryptography Extension KeyStore
- **PKCS12 / P12** – Public-Key Cryptography Standards #12 (`.p12`, `.pfx`)
- **DKS** – Domain KeyStore (used for DNSSEC)
- **PKCS11** – Hardware or software-based tokens (via SunPKCS11 provider)

[Java Keystore Types – Oracle Documentation](https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html#keystore-types)

#### Plain Certificate Files

These are loaded as standalone X.509 certificates or bundles:

- **PEM** – Privacy-Enhanced Mail format (`.pem`)
- **CRT** – X.509 certificate (`.crt`, often in PEM encoding)

Supports **single certificates** and **PEM bundles** (e.g. full chains).

## 🚀 Installation and Usage

### Kubernetes

Deploy manifests from `deploy/kubernetes`:

```bash
kubectl apply -f deploy/kubernetes/
```

- Mount your certificate secrets as files or Kubernetes `Secret`s
- Provide `certalert.yaml` via a `ConfigMap` and mount it at `/config/certalert.yaml`

## ⚙️ Configuration

```yaml
certalert:
  check-interval: 2m
  certificates: ...
  dashboard:
    warning-threshold: 20d
    critical-threshold: 3d
```

## 🔑 Providing Credentials

CertAlert supports dynamic credential resolution using a flexible prefix scheme:

| Prefix        | Description                                  | Example                                      |
| ------------- | -------------------------------------------- | -------------------------------------------- |
| `env:`        | Resolves from environment variables          | `env:PATH`                                   |
| `file:`       | Reads from plaintext or key-value files      | `file:/config/app.txt//KeyName`              |
| `json:`       | Parses from JSON using dot notation          | `json:/config/app.json//database.host`       |
| `yaml:`       | Extracts from YAML using dot notation        | `yaml:/config/app.yaml//servers.0.host`      |
| `ini:`        | Extracts from INI using `Section.Key` format | `ini:/config/app.ini//Database.Password`     |
| `properties:` | Loads from `.properties` files               | `properties:/config/app.properties//db.user` |
| `toml:`       | Loads from TOML using dot notation           | `toml:/config/app.toml//database.host`       |
| _(no prefix)_ | Treated as literal value                     | `my-secret-token-123`                        |

## 📈 Prometheus Metrics

CertAlert exposes a set of Prometheus metrics to monitor certificate expirations and validity.

**Note:** Metrics are only emitted **after** each polling interval, so they may not appear immediately.

### Available Metrics

1. **`certalert_certificate_expiration_seconds`**

   - **Type:** Gauge
   - **Description:** Unix timestamp when the certificate expires (`Not After`)
   - **Labels:** `certificate_name`, `alias`

2. **`certalert_certificate_validity`**

   - **Type:** Gauge
   - **Description:** Validity state (`0 = valid`, `1 = invalid or expired`)
   - **Labels:** `certificate_name`, `alias`

👉 Metrics are scraped at `/metrics`.

## 🤝 Contributing

We welcome contributions of all kinds!
Please see our [CONTRIBUTING.md](.github/CONTRIBUTING.md) for guidelines.

1.  Fork the repository
2.  Create a feature branch
3.  Run tests and verify
4.  Submit a Pull Request with a clear description

## 📄 License

This project is licensed under the MIT License.
See the [LICENSE](LICENSE) file for details.
