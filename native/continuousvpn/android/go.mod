module uk.co.traynor.privategallery/continuousvpn/android

go 1.20

require (
	golang.org/x/sys v0.6.0
	golang.zx2c4.com/wireguard v0.0.0-20230223181233-21636207a675
	uk.co.traynor.privategallery/continuousvpn/gate v0.0.0
	uk.co.traynor.privategallery/continuousvpn/transport v0.0.0
)

require (
	golang.org/x/crypto v0.7.0 // indirect
	golang.org/x/net v0.8.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	gvisor.dev/gvisor v0.0.0-20221203005347-703fd9b7fbc0
	uk.co.traynor.privategallery/continuousvpn/readiness v0.0.0
)

replace uk.co.traynor.privategallery/continuousvpn/gate => ../gate

replace uk.co.traynor.privategallery/continuousvpn/transport => ../transport

replace uk.co.traynor.privategallery/continuousvpn/readiness => ../readiness
