"""Select the complete broad inventory in reproducible, disjoint instrumentation batches."""
from collections import defaultdict
from pathlib import Path
import sys

from verify_novaos99_full_evidence import PACKAGE, discovered_cases, fixture_selectors, manifest_cases

# These two methods explicitly require an unused, unconfigured fixture workspace.
FRESH = {
    f"{PACKAGE}.LauncherWorkspace70Test#directPrimaryVaultHidesOnlyInternalNavigationUntilReturningHome",
    f"{PACKAGE}.PrimaryGalleryDeviceRepair1762Test#seededPrimaryGallerySurvivesHiddenNavigationAndRevokesOnLock",
}


def batches(inventory, dedicated_classes, dedicated_cases, fresh, limit=120):
    classes = defaultdict(set)
    for identity in sorted(inventory):
        name, method = identity.split("#")
        classes[name].add(method)
    pending = []
    count = 0
    if not fresh.issubset(inventory) or not dedicated_cases.issubset(inventory):
        raise ValueError("Required fixture missing from Android discovery")
    for case in sorted(fresh):
        if case in dedicated_cases or case.split("#")[0] in dedicated_classes:
            raise ValueError("Fresh workspace case overlaps a dedicated fixture")
        yield case
    for name, methods in classes.items():
        if name in dedicated_classes:
            continue
        group = sorted(f"{name}#{m}" for m in methods if f"{name}#{m}" not in dedicated_cases | fresh)
        if not group:
            continue
        if pending and count + len(group) > limit:
            yield ",".join(pending)
            pending, count = [], 0
        pending.extend([name] if len(group) == len(methods) else group)
        count += len(group)
    if pending:
        yield ",".join(pending)


if __name__ == "__main__":
    inventory = discovered_cases(Path(sys.argv[1]).read_text())
    for batch in batches(inventory, fixture_selectors(), manifest_cases(), FRESH):
        print(batch)
