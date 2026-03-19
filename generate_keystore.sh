#!/bin/bash

# Android Music 3 - Generate Keystore Script
# This script generates a signing keystore for the Android app

set -e

echo "==================================="
echo "Android Music 3 - Generate Keystore"
echo "==================================="
echo ""

# Default values
KEYSTORE_FILE="keystore.jks"
KEYSTORE_PASSWORD="androidmusic3"
KEY_ALIAS="androidmusic3"
KEY_PASSWORD="androidmusic3"
VALIDITY=10000

# Check if keystore already exists
if [ -f "$KEYSTORE_FILE" ]; then
    echo "Warning: Keystore file $KEYSTORE_FILE already exists."
    read -p "Do you want to overwrite it? (y/n): " overwrite
    if [ "$overwrite" != "y" ] && [ "$overwrite" != "Y" ]; then
        echo "Keystore generation cancelled."
        exit 0
    fi
    rm -f "$KEYSTORE_FILE"
fi

# Generate keystore
echo "Generating keystore..."
keytool -genkey \
    -v \
    -keystore "$KEYSTORE_FILE" \
    -keyalg RSA \
    -keysize 2048 \
    -validity $VALIDITY \
    -storepass "$KEYSTORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -alias "$KEY_ALIAS" \
    -dname "CN=AndroidMusic3, OU=Development, O=MusicPlayer, L=City, S=State, C=US"

if [ $? -eq 0 ]; then
    echo ""
    echo "==================================="
    echo "Keystore generated successfully!"
    echo "==================================="
    echo "Keystore file: $KEYSTORE_FILE"
    echo "Keystore password: $KEYSTORE_PASSWORD"
    echo "Key alias: $KEY_ALIAS"
    echo "Key password: $KEY_PASSWORD"
    echo ""
    echo "Please store these values securely!"
    echo ""
else
    echo ""
    echo "Error: Failed to generate keystore."
    exit 1
fi
