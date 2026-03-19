#!/bin/bash

# Android Music 3 - Build APK Script
# This script builds a signed APK and optionally installs it to a connected device

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "==================================="
echo "Android Music 3 - Build APK"
echo "==================================="
echo ""

# Check if keystore exists
if [ ! -f "keystore.jks" ]; then
    echo -e "${RED}Error: keystore.jks not found!${NC}"
    echo "Please run './generate_keystore.sh' first to generate a signing keystore."
    exit 1
fi

# Check if Gradle wrapper exists
if [ ! -f "gradlew" ]; then
    echo -e "${RED}Error: gradlew not found!${NC}"
    echo "This script must be run from the project root directory."
    exit 1
fi

# Make gradlew executable
chmod +x gradlew

echo -e "${YELLOW}Building debug APK...${NC}"
./gradlew assembleDebug

if [ $? -ne 0 ]; then
    echo -e "${RED}Debug build failed!${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}Debug APK built successfully!${NC}"
echo "Location: app/build/outputs/apk/debug/app-debug.apk"
echo ""

# Build release APK
echo -e "${YELLOW}Building release APK...${NC}"
./gradlew assembleRelease

if [ $? -ne 0 ]; then
    echo -e "${RED}Release build failed!${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}Release APK built successfully!${NC}"
echo "Location: app/build/outputs/apk/release/app-release.apk"
echo ""

# Ask user if they want to install the APK
read -p "Do you want to install the release APK to a connected device? (y/n): " install

if [ "$install" = "y" ] || [ "$install" = "Y" ]; then
    # Check if device is connected
    if ! adb devices | grep -q "device$"; then
        echo -e "${RED}Error: No Android device found connected via ADB.${NC}"
        echo "Please connect your Android device with USB debugging enabled."
        exit 1
    fi

    echo -e "${YELLOW}Installing APK to device...${NC}"
    adb install -r app/build/outputs/apk/release/app-release.apk

    if [ $? -eq 0 ]; then
        echo ""
        echo -e "${GREEN}APK installed successfully!${NC}"
        echo "You can now find 'Android Music 3' in your app drawer."
    else
        echo -e "${RED}APK installation failed!${NC}"
        exit 1
    fi
fi

echo ""
echo "==================================="
echo "Build complete!"
echo "==================================="
