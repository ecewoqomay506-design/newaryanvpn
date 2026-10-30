#!/usr/bin/env python3
"""Offline source-integrity checks; these do not replace Android compilation or device testing."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def require(ok: bool, message: str) -> None:
    if not ok:
        print(f"FAIL: {message}", file=sys.stderr)
        raise SystemExit(1)
    print(f"PASS: {message}")

# Parse resources and ensure the two shipped locales contain matching keys.
for xml_file in (ROOT / "app/src/main").rglob("*.xml"):
    ET.parse(xml_file)
values = ROOT / "app/src/main/res/values/strings.xml"
values_fa = ROOT / "app/src/main/res/values-fa/strings.xml"
def string_keys(path: Path) -> set[str]:
    return {node.attrib["name"] for node in ET.parse(path).getroot().findall("string")}
require(string_keys(values) == string_keys(values_fa), "English and Farsi string resources have matching keys")

# Ensure source defaults do not embed any server access credentials.
defaults = (ROOT / "app/src/main/java/com/aryan/vpn/core/DefaultProfiles.kt").read_text()
require("= emptyList()" in defaults and "vless://" not in defaults, "no bundled VLESS profiles or credentials")

# Confirm secrets are encrypted before persistence and Keystore-backed.
repo = (ROOT / "app/src/main/java/com/aryan/vpn/data/Repositories.kt").read_text()
require('"AndroidKeyStore"' in repo and '"AES/GCM/NoPadding"' in repo, "profile storage uses Android Keystore AES-GCM")
require('putString(ENCRYPTED_LIST, encrypted)' in repo, "profile persistence stores ciphertext")
require('putString("list", arr.toString())' not in repo, "no plaintext profile list write")

# Ensure fallback is used by both the engine and network probe.
profile = (ROOT / "app/src/main/java/com/aryan/vpn/core/VlessProfile.kt").read_text()
config = (ROOT / "app/src/main/java/com/aryan/vpn/core/ConfigBuilder.kt").read_text()
latency = (ROOT / "app/src/main/java/com/aryan/vpn/core/LatencyTester.kt").read_text()
require("val connectionHost" in profile, "IPv6 profile hostname fallback is defined")
require('.put("server", p.connectionHost)' in config, "engine config uses the resolved fallback host")
require("network.getAllByName(p.connectionHost)" in latency, "endpoint probe resolves through the underlying network")
require('endpointIdentificationAlgorithm = "HTTPS"' in latency, "endpoint probe enables TLS hostname verification")

# Ensure the engine build path is pinned and missing binaries are diagnosed clearly.
build_script = (ROOT / "scripts/build-libbox.sh").read_text()
gradle = (ROOT / "app/build.gradle.kts").read_text()
require('PINNED_TAG="v1.9.7"' in build_script, "engine build script pins sing-box v1.9.7")
require("Missing app/libs/libbox.aar" in gradle, "Gradle reports a direct missing-engine instruction")
require((ROOT / "app/src/main/java/com/aryan/vpn/vpn/PlatformBridge.kt").is_file(), "platform bridge source is present for API compile-check")

# Resolve app string references against the resource catalog.
resource_keys = string_keys(values)
refs = set()
for source in (ROOT / "app/src/main").rglob("*.kt"):
    refs.update(re.findall(r"R\.string\.([A-Za-z0-9_]+)", source.read_text()))
require(not (refs - resource_keys), f"all referenced app strings exist ({len(refs)} keys checked)")

require(not list(ROOT.rglob("*.apk")), "no APK is misrepresented as a build output")
print("Offline source checks passed. Android build and device behavior remain unverified.")
