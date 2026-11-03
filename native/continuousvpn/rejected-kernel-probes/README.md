# Rejected kernel probe reservation experiment

These Java sources are security investigation evidence, not application sources or an approved implementation. They are intentionally outside the Android source tree. Do not integrate this helper or treat its113 passing assertions as containment proof.

Independent review found that an inherited TCP connect failure releases the kernel source-port binding before the helper can revoke its native tuple lease. Keeping a duplicate descriptor does not fix the Linux failed-connect unbinding. Actual loopback counterexamples additionally show inherited SOCKS routing, connect-after-cancellation and input-skip behavior outside the intended owner policy. See `docs/release17103/PROBE_RESERVATIONS_REVIEW_REJECTED.md` and the two counterexample classes here.

The old focused runner is retained unchanged as historical evidence; its former application-relative source path is no longer valid. This experiment is not packaged, loaded, or used for Android probes. Future containment work must remove the kernel-tuple ownership premise rather than weaken revocation or packet assertions.

Reproduce the actual failures from the repository root using Java17:

```sh
probe_classes=$(mktemp -d)
javac --release 17 -d "$probe_classes" native/continuousvpn/rejected-kernel-probes/ProbeReservations.java native/continuousvpn/rejected-kernel-probes/FailedConnectRetention.java native/continuousvpn/rejected-kernel-probes/InheritedSocketPaths.java
java -cp "$probe_classes" FailedConnectRetention
java -cp "$probe_classes" InheritedSocketPaths
rm -r "$probe_classes"
```

Expected `BUG:` lines demonstrate rejection; a zero process exit is not a green security verdict. All destinations are synthetic loopback sockets.
