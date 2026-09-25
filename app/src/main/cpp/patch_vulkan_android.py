"""Keep Vulkan 1.1 entry points out of the Android API 26 ELF import table.

The Vulkan backend still requires Vulkan 1.2 at runtime. Its existing Vulkan-Hpp
instance dispatcher resolves newer functions only after that feature check.
The app and CPU fallback therefore retain their existing Android minimum API.
"""
from pathlib import Path
import re
import subprocess
import sys

root = Path(sys.argv[1]) / "ggml"
revision = "4bf5f6000653b7881d00963cd6ddb665ccd62a8d"
path = "src/ggml-vulkan/ggml-vulkan.cpp"
actual = subprocess.check_output(["git", "-C", str(root), "rev-parse", "HEAD"], text=True).strip()
if actual != revision:
    raise RuntimeError(f"Unreviewed ggml revision for Android Vulkan patch: {actual}")
text = subprocess.check_output(["git", "-C", str(root), "show", f"{revision}:{path}"], text=True)
calls = [
    "vkGetPhysicalDeviceFeatures2(device->physical_device, &device_features2);",
    "vkGetPhysicalDeviceFeatures2(physical_device, &device_features2);",
    "vkGetPhysicalDeviceFeatures2(vkdev, &device_features2);",
]
for call in calls:
    if text.count(call) != 1:
        raise RuntimeError(f"Pinned Vulkan call changed: {call}")
    text = text.replace(call, "VULKAN_HPP_DEFAULT_DISPATCHER." + call)
if re.search(r"(?<![.\w])vkGetPhysicalDeviceFeatures2\s*\(", text):
    raise RuntimeError("Unpatched direct Vulkan 1.1 feature-query import")
(root / path).write_text(text)
