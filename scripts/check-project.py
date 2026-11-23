#!/usr/bin/env python3
"""Sanity checks before building with Gradle; needs only Python stdlib.

Catches the exact AAPT resource-linker failure seen in GitHub Actions.
This is not a replacement for compiling Android resources with AAPT2.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
config = ROOT / 'app/src/main/res/xml/accessibility_config.xml'
manifest = ROOT / 'app/src/main/AndroidManifest.xml'
workflow = ROOT / '.github/workflows/android.yml'

service = ET.parse(config).getroot()
assert service.tag == 'accessibility-service', f'Unexpected root in {config}'
assert ANDROID + 'flags' not in service.attrib, 'Invalid android:flags: use android:accessibilityFlags'
assert service.get(ANDROID + 'accessibilityFlags') == 'flagReportViewIds', \
    'Accessibility XML must use android:accessibilityFlags="flagReportViewIds"'
assert service.get(ANDROID + 'canPerformGestures') == 'true', 'Missing gesture ability'
assert service.get(ANDROID + 'canRetrieveWindowContent') == 'true', 'Missing text node permission'
app = ET.parse(manifest).getroot().find('application')
assert app is not None, 'Missing application tag'
service_tags = app.findall('service')
assert any(t.get(ANDROID + 'name') == '.MathAccessibilityService' and
           any(m.get(ANDROID + 'name') == 'android.accessibilityservice' and
               m.get(ANDROID + 'resource') == '@xml/accessibility_config'
               for m in t.findall('meta-data'))
           for t in service_tags), 'Accessibility service configuration missing from manifest'
workflow_text = workflow.read_text(encoding='utf-8')
assert 'sdkmanager tools' not in workflow_text, 'Obsolete Android SDK tools package requested'
java_sources = list((ROOT / 'app/src/main/java').rglob('*.java'))
assert java_sources, 'No Java source files found'
assert not any('setTextAllCaps(' in p.read_text(encoding='utf-8') for p in java_sources), \
    'Unsupported android.widget.Button#setTextAllCaps: use setAllCaps(false)'
assert 'gradle --no-daemon' in workflow_text, 'Gradle build is missing'
assert 'scripts/emulator-smoke.sh' in workflow_text, 'Emulator launch smoke test missing'
print('PASS: Android XML, manifest, unsupported Button API, build and emulator workflow checked.')
