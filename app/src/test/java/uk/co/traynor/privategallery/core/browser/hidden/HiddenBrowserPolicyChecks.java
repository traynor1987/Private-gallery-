package uk.co.traynor.privategallery.core.browser.hidden;

public final class HiddenBrowserPolicyChecks {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        long[] now={0};
        OwnerDiagnosticsGate gate=new OwnerDiagnosticsGate(()->now[0]);
        for(int i=0;i<6;i++) check(!gate.tap());
        check(gate.tap()); check(!gate.tap());
        gate.reset(); for(int i=0;i<6;i++) gate.tap(); now[0]=31_000; check(!gate.tap());
        gate.reset(); now[0]=100; gate.tap(); now[0]=99; check(!gate.tap());
        HiddenConnectionPolicy route=new HiddenConnectionPolicy();
        check(route.mode()==HiddenConnectionPolicy.Mode.TOR_ONLY);
        check(!route.allowed());
        long first=route.begin(HiddenConnectionPolicy.Mode.TOR_ONLY,true);
        check(!route.allowed());
        check(route.canEstablish(first));
        check(route.ready(first,true,false)); check(route.allowed());
        long second=route.begin(HiddenConnectionPolicy.Mode.VPN_ONLY,true);
        check(!route.allowed()); check(!route.ready(first,true,true)); check(!route.allowed());
        check(!route.ready(second,false,false)); check(!route.allowed());
        check(route.ready(second,false,true)); check(route.allowed());
        route.disconnect(); check(!route.canEstablish(second)); check(!route.allowed()); check(!route.ready(second,false,true));
        long unavailable=route.begin(HiddenConnectionPolicy.Mode.VPN_TOR,true);
        check(!route.canEstablish(unavailable)); check(!route.ready(unavailable,true,true)); check(!route.allowed());
        long locked=route.begin(HiddenConnectionPolicy.Mode.TOR_ONLY,false);
        check(!route.canEstablish(locked)); check(!route.ready(locked,true,false)); check(!route.allowed());
        check(HiddenConnectionPolicy.Mode.parse("unknown")==HiddenConnectionPolicy.Mode.TOR_ONLY);
        check(HiddenConnectionPolicy.safeUrl("https://example.org/a"));
        for(String bad:new String[]{"file:///etc/passwd","content://app/secret","javascript:alert(1)","intent://x","https://user:pass@example.org/","http://127.0.0.1/","http://192.168.1.1/","http://[::1]/","http://localhost/","http://2130706433/","http://0x7f000001/"}) check(!HiddenConnectionPolicy.safeUrl(bad));
        String onion="http://"+"a".repeat(56)+".onion/path";
        check(HiddenConnectionPolicy.safeUrl(onion,HiddenConnectionPolicy.Mode.TOR_ONLY));
        check(!HiddenConnectionPolicy.safeUrl(onion,HiddenConnectionPolicy.Mode.VPN_ONLY));
        check(!HiddenConnectionPolicy.safeUrl(onion,HiddenConnectionPolicy.Mode.VPN_TOR));
        check(!HiddenConnectionPolicy.safeUrl("http://short.onion/",HiddenConnectionPolicy.Mode.TOR_ONLY));
        check(!HiddenConnectionPolicy.safeUrl("http://"+"0".repeat(56)+".onion/",HiddenConnectionPolicy.Mode.TOR_ONLY));
        check(HiddenConnectionPolicy.safeUrl("https://bbc.co.uk/",HiddenConnectionPolicy.Mode.TOR_ONLY));
        check(HiddenConnectionPolicy.safeUrl("https://bbc.co.uk/",HiddenConnectionPolicy.Mode.VPN_ONLY));
        check(!HiddenConnectionPolicy.safeUrl("https://bbc.co.uk/",HiddenConnectionPolicy.Mode.VPN_TOR));
        System.out.println("Hidden browser/diagnostics policy: "+checks+" checks passed");
    }
}
