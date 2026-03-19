#!/bin/bash

# Android Music 3 - Quick Build and Install Script
# One-command script to build and install the APK

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo "==================================="
echo "Android Music 3 - Build & Install"
echo "==================================="
echo ""

# Step 1: Check keystore
echo -e "${BLUE}[1/4] Checking keystore...${NC}"
if [ ! -f "keystore.jks" ]; then
    echo -e "${YELLOW}Keystore not found. Generating...${NC}"
    ./generate_keystore.sh
else
    echo -e "${GREEN}Keystore found.${NC}"
fi
echo ""

# Step 2: Clean build
echo -e "${BLUE}[2/4] Cleaning previous builds...${NC}"
./gradlew clean
echo -e "${GREEN}Clean complete.${NC}"
echo ""

# Step 3: Build APK
echo -e "${BLUE}[3/4] Building release APK...${NC}"
./gradlew assembleRelease

if [ $? -ne 0 ]; then
    echo -e "${RED}Build failed!${NC}"
    exit 1
fi

echo -e "${GREEN}APK built successfully!${NC}"
echo "Location: app/build/outputs/apk/release/app-release.apk"
echo ""

# Step 4: Install to device
echo -e "${BLUE}[4/4] Installing to device...${NC}"

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo -e "${YELLOW}No Android device found.${NC}"
    echo "Please connect your Android device with USB debugging enabled."
    echo ""
    read -p "Press Enter when your device is connected..."

    if ! adb devices | grep -q "device$"; then
        echo -e "${RED}No device found. Installation skipped.${NC}"
        echo ""
        echo "You can manually install the APK using:"
        echo "adb install -r app/build/outputs/apk/release/app-release.apk"
        exit 0
    fi
fi

# Install the APK
adb install -r app/build/outputs/apk/release/app-release.apk

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}==================================="
    echo "SUCCESS!${NC}"
    echo -e "${GREEN}==================================="
    echo ""
    echo "Android Music 3 has been installed on your device!"
    echo "You can now find it in your app drawer."
    echo ""
else
    echo -e "${RED}Installation failed!${NC}"
    echo "You can try installing manually with:"
    echo "adb install -r app/build/outputs/apk/release/app-release.apk"
    exit 1
fi
