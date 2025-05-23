#!/bin/bash

set -euo pipefail

# Define certs and their optional passwords
declare -A certs=(
  ["with_password"]="password"
  ["without_password"]=""
  ["intermediate"]="password"
  ["root"]="password"
  ["final"]="password"
)

CERT_DIR="./tests/certs/p12"
mkdir -p "${CERT_DIR}"
pushd "${CERT_DIR}" >/dev/null

# Generate certs with or without password
for name in "${!certs[@]}"; do
  password="${certs[$name]}"

  # Generate private key
  if [ -z "$password" ]; then
    openssl genpkey -algorithm RSA -out "${name}.key"
  else
    openssl genpkey -algorithm RSA -out "${name}.key" -pass pass:"${password}"
  fi

  # Self-signed certificate
  openssl req -new -x509 -key "${name}.key" \
    ${password:+-passin pass:$password} \
    -out "${name}.crt" -days 365 -subj "/CN=${name}"

  # PKCS#12 export
  openssl pkcs12 -export -out "${name}.p12" \
    -inkey "${name}.key" -in "${name}.crt" \
    ${password:+-passin pass:$password} \
    -password pass:"${password}"
done

# Create a broken PKCS#12 file
echo "broken" >broken.p12

# Create invalid extension file
echo "invalid" >cert.invalid

# Create file with no extension
echo "no-extension" >no_extension

# Create PKCS#12 file with certificate chain
cat final.crt intermediate.crt root.crt >chain.crt
openssl pkcs12 -export -out chain.p12 \
  -inkey final.key -in final.crt \
  -certfile chain.crt \
  -passin pass:password \
  -password pass:password

# Certificate with empty subject
openssl genpkey -algorithm RSA -out empty_subject.key -pass pass:password
openssl req -new -x509 -key empty_subject.key -out empty_subject.crt -days 365 -passin pass:password -subj "/"
openssl pkcs12 -export -out empty_subject.p12 -inkey empty_subject.key -in empty_subject.crt -passin pass:password -password pass:password

popd >/dev/null

# Generate certalert config block
echo
echo "Generated certalert configuration:"
echo "certalert:"
echo "  certificates:"

for name in "${!certs[@]}"; do
  echo "    - name: ${name}"
  echo "      type: pkcs12"
  echo "      path: ${CERT_DIR}/${name}.p12"
  echo "      password: ${certs[$name]}"
done

echo "    - name: chain"
echo "      type: pkcs12"
echo "      path: ${CERT_DIR}/chain.p12"
echo "      password: password"

echo "    - name: empty_subject"
echo "      type: pkcs12"
echo "      path: ${CERT_DIR}/empty_subject.p12"
echo "      password: password"

echo "    - name: broken"
echo "      type: pkcs12"
echo "      path: ${CERT_DIR}/broken.p12"

echo "    - name: invalid"
echo "      type: pkcs12"
echo "      path: ${CERT_DIR}/cert.invalid"

echo "    - name: no_extension"
echo "      type: pkcs12"
echo "      path: ${CERT_DIR}/no_extension"
