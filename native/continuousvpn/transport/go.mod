module uk.co.traynor.privategallery/continuousvpn/transport

go 1.20

require (
	golang.zx2c4.com/wireguard v0.0.0-20230223181233-21636207a675
	golang.org/x/sys v0.6.0
	uk.co.traynor.privategallery/continuousvpn/gate v0.0.0
)

require (
	golang.org/x/crypto v0.7.0 // indirect
	golang.org/x/net v0.8.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
)

replace uk.co.traynor.privategallery/continuousvpn/gate => ../gate
