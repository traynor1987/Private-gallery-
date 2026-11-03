package transport

import "testing"

func TestNumericStatisticsAreBoundedAndRequireLivePeer(t *testing.T) {
	f, _ := fixture(t, 1)
	tr, e := f.Replace(options(newFakeBind(), 1))
	if e != nil {
		t.Fatal(e)
	}
	s, e := tr.Statistics()
	if e != nil || s.HandshakeSeconds != 0 || s.HandshakeNanos != 0 || s.RxBytes != 0 || s.TxBytes != 0 {
		t.Fatal(s, e)
	}
	tr.BeginProbe()
	s, e = tr.Statistics()
	if e != nil || s.TxBytes < 0 || s.RxBytes < 0 {
		t.Fatal(s, e)
	}
	tr.Close()
	if _, e = tr.Statistics(); e == nil {
		t.Fatal("closed statistics")
	}
}
func TestStatisticsRejectOversizedAndMalformedFields(t *testing.T) {
	for _, v := range []string{"rx_bytes=-1\n", "tx_bytes=9223372036854775808\n", "last_handshake_time_nsec=1000000000\n", "rx_bytes=1\nrx_bytes=2\n"} {
		if _, e := parseStatistics([]byte(v)); e == nil {
			t.Fatal(v)
		}
	}
	b := &boundedStatistics{}
	if _, e := b.Write(make([]byte, 4097)); e == nil {
		t.Fatal("unbounded output")
	}
}
