#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
output_dir="$(mktemp -d)"
trap 'rm -rf "$output_dir"' EXIT
javac -d "$output_dir" app/src/main/java/com/unitepaintball/app/SharedFeaturePolicy.java tests/SharedFeaturePolicyTest.java app/src/main/java/com/unitepaintball/app/GiftBookingPricing.java tests/GiftBookingPricingTest.java app/src/main/java/com/unitepaintball/app/GiftCertificatePolicy.java tests/GiftCertificatePolicyTest.java app/src/main/java/com/unitepaintball/app/AppConfig.java app/src/main/java/com/unitepaintball/app/NetworkRetryPolicy.java tests/NetworkRetryPolicyTest.java app/src/main/java/com/unitepaintball/app/StartupSyncPolicy.java app/src/main/java/com/unitepaintball/app/PackInstaller.java tests/StartupSyncPolicyTest.java tests/PackInstallerTest.java
java -cp "$output_dir" SharedFeaturePolicyTest
java -cp "$output_dir" GiftBookingPricingTest
java -cp "$output_dir" GiftCertificatePolicyTest
java -cp "$output_dir" NetworkRetryPolicyTest
java -cp "$output_dir" StartupSyncPolicyTest
java -cp "$output_dir" com.unitepaintball.app.PackInstallerTest
node --test tests/ownership.test.cjs
