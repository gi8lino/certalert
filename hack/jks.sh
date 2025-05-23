#!/bin/bash

set -euo pipefail

certs=("regular" "chain" "intermediate" "root")
extra_certs=("expired" "expiring-soon" "pkcs12")
STOREPASS="password"
KEYPASS="password"
CERT_DIR="./certs/jks"

mkdir -p "${CERT_DIR}"
pushd "${CERT_DIR}" >/dev/null

# Generate regular JKS certificates
for name in "${certs[@]}"; do
  keytool -genkeypair -keyalg RSA \
    -alias "${name}" \
    -keystore "${name}.jks" \
    -storepass "${STOREPASS}" \
    -keypass "${KEYPASS}" \
    -validity 365 \
    -dname "CN=${name}, OU=MyOrganization, O=MyCompany, L=MyCity, ST=MyState, C=US" \
    -storetype JKS \
    -noprompt
done

# Expired certificate (expired 1 day ago)
keytool -genkeypair -keyalg RSA \
  -alias expired \
  -keystore expired.jks \
  -storepass "${STOREPASS}" \
  -keypass "${KEYPASS}" \
  -validity 1 \
  -dname "CN=expired" \
  -storetype JKS \
  -noprompt

# Certificate expiring in 7 days
keytool -genkeypair -keyalg RSA \
  -alias expiring-soon \
  -keystore expiring-soon.jks \
  -storepass "${STOREPASS}" \
  -keypass "${KEYPASS}" \
  -validity 7 \
  -dname "CN=expiring-soon" \
  -storetype JKS \
  -noprompt

# Chain keystore with root, intermediate, and leaf keys
for alias in root intermediate leaf; do
  keytool -genkeypair -keyalg RSA \
    -alias "${alias}" \
    -keystore chain.jks \
    -storepass "${STOREPASS}" \
    -keypass "${KEYPASS}" \
    -validity 365 \
    -dname "CN=${alias}" \
    -storetype JKS \
    -noprompt
done

# PKCS12 file
keytool -genkeypair -keyalg RSA \
  -alias pkcs12 \
  -keystore pkcs12.p12 \
  -storepass "${STOREPASS}" \
  -keypass "${KEYPASS}" \
  -validity 365 \
  -dname "CN=pkcs12" \
  -storetype PKCS12 \
  -noprompt

# Create broken and invalid files
echo "broken" >broken.jks
echo "invalid" >cert.invalid

popd >/dev/null

# Print configuration for certalert
echo
echo "Generated certalert configuration:"
echo "certalert:"
echo "  certificates:"

for name in "${certs[@]}" "${extra_certs[@]}"; do
  ext="jks"
  [[ "$name" == "pkcs12" ]] && ext="p12"
  echo "    - name: ${name}"
  echo "      type: jks"
  echo "      path: ${CERT_DIR}/${name}.${ext}"
  echo "      password: ${STOREPASS}"
done

echo "    - name: broken"
echo "      type: jks"
echo "      path: ${CERT_DIR}/broken.jks"

echo "    - name: invalid"
echo "      type: jks"
echo "      path: ${CERT_DIR}/cert.invalid"
