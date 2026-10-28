"""Read the built APK identity before any emulator-private evidence operation."""
import os
from pathlib import Path
import re
import subprocess
import sys


def parse_identity(badging):
    match = re.search(r"^package: name='([^']+)'", badging, re.MULTILINE)
    if not match or match.group(1) != 'uk.co.traynor.privategallery.hiddenprototype':
        raise ValueError('Focused evidence requires the isolated test-only application ID')
    return match.group(1)


if __name__ == '__main__':
    sdk = Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ['ANDROID_HOME'])
    aapt = sdk / 'build-tools' / '36.0.0' / 'aapt2'
    result = subprocess.run([str(aapt), 'dump', 'badging', sys.argv[1]],
                            check=True, capture_output=True, text=True)
    print(parse_identity(result.stdout))
