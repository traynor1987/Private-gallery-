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
        HiddenVpnRetirement retirement=new HiddenVpnRetirement(5000);
        check(retirement.observe(0,true,true,true)==HiddenVpnRetirement.Decision.SWEEP);
        check(retirement.observe(100,true,true,false)==HiddenVpnRetirement.Decision.SWEEP);
        check(retirement.observe(900,true,true,true)==HiddenVpnRetirement.Decision.SWEEP); // Cached replacement resets absence.
        check(retirement.observe(1000,true,true,false)==HiddenVpnRetirement.Decision.SWEEP);
        check(retirement.observe(1999,true,true,false)==HiddenVpnRetirement.Decision.SWEEP);
        check(retirement.observe(2000,true,true,false)==HiddenVpnRetirement.Decision.RELEASE);
        check(retirement.observe(2100,false,true,true)==HiddenVpnRetirement.Decision.STALE); // Old queued owner cannot sweep new child.
        HiddenVpnRetirement unknown=new HiddenVpnRetirement(5000);
        check(unknown.observe(0,true,true,false)==HiddenVpnRetirement.Decision.SWEEP);
        check(unknown.observe(1000,true,false,false)==HiddenVpnRetirement.Decision.SWEEP);
        check(unknown.observe(5000,true,false,false)==HiddenVpnRetirement.Decision.EXHAUSTED);
        check(unknown.observe(6000,true,true,false)==HiddenVpnRetirement.Decision.EXHAUSTED);
        check(unknown.observe(7000,true,true,false)==HiddenVpnRetirement.Decision.EXHAUSTED);
        check(new HiddenVpnRetirement(5000).observe(5000,true,true,true)==HiddenVpnRetirement.Decision.EXHAUSTED);
        check(HiddenVpnRetirement.ownsProcess(10,10,20,30,"app:child","app:child"));
        check(!HiddenVpnRetirement.ownsProcess(11,10,20,30,"app:child","app:child"));
        check(!HiddenVpnRetirement.ownsProcess(10,10,30,30,"app:child","app:child"));
        check(!HiddenVpnRetirement.ownsProcess(10,10,20,30,"app:tor","app:child"));
        check(!HiddenVpnRetirement.removableTask("Main","Child"));
        check(HiddenVpnRetirement.removableTask("Child","Child"));
        check(HiddenVpnRetirement.pendingTask(true,"Main","Child","Child"));
        check(HiddenVpnRetirement.pendingTask(false,null,null,"Child"));
        check(!HiddenVpnRetirement.pendingTask(true,"Main","Main","Child"));
        System.out.println("Hidden browser/diagnostics policy: "+checks+" checks passed");
    }
}
