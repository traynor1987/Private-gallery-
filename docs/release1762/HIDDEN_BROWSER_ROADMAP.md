# Future release — Secure Hidden VPN Interactive Browser

The owner deferred interactive Hidden VPN browser functionality from 1.7.6.2. The existing GET-only renderer and its fail-closed VPN/Tor transport remain in place. Session-only bookmarks and history must remain independent of Primary.

Future scope: supported request-body transport and POST, JavaScript-dependent websites, forms and uploads, and full Primary/Hidden browser compatibility. Independently verify VPN-only routing, DNS isolation, no direct fallback, session revocation, mode switching and strict Tor separation. Tor must retain JavaScript-disabled defaults. Do not treat WebView request interception as exposing POST bodies; an isolated, supported routing architecture requires security review and focused device tests.

This roadmap is not an implementation, test result, security approval or promise of a particular release number.
