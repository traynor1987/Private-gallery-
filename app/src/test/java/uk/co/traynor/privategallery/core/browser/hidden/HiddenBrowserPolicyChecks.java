package uk.co.traynor.privategallery.core.browser.hidden;

public final class HiddenBrowserPolicyChecks {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value) throw new AssertionError("check " + checks); }
    public static void main(String[] args) {
        HiddenPageLoadDeadline page=new HiddenPageLoadDeadline();
        check(!page.expired(100_000));
        page.begin(100);
        check(!page.expired(60_099));check(page.expired(60_100));
        check(!page.expired(60_101)); // Deadline is consumed exactly once.
        page.begin(200);page.visible();check(!page.expired(100_000));
        page.begin(300);page.begin(400);check(!page.expired(60_300));check(page.expired(60_400));
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
        // Exercise the same resolver boundary used by the Activity, not just safeUrl on its output.
        int[] searches={0};
        java.util.function.Function<String,String> search=query->{searches[0]++;return "https://duckduckgo.com/?q=private-value";};
        String[] refused={"file:///private.jpg","content://private/token","unknown://private/token",
            "https://","https://bad host/private","http:/private","javascript:private-token",
            "https://user:secret@example.org/private","http://127.0.0.1/private","http://localhost/private",
            "https://device.local/private","127.0.0.1/private","device.internal/private","user:secret@example.org/private",
            "localhost","[::1]","https://example.org:8443/private"};
        for(HiddenConnectionPolicy.Mode selected:HiddenConnectionPolicy.Mode.values()) {
            for(String value:refused) {
                check(HiddenConnectionPolicy.resolveAddress(value,selected,search)==null);
                check(searches[0]==0);
            }
        }
        check("https://example.org/path".equals(HiddenConnectionPolicy.resolveAddress("example.org/path",HiddenConnectionPolicy.Mode.VPN_ONLY,search)));
        check("https://example.org/path".equals(HiddenConnectionPolicy.resolveAddress("https://example.org/path",HiddenConnectionPolicy.Mode.VPN_ONLY,search)));
        String bareOnion="a".repeat(56)+".onion/path";
        check(("http://"+bareOnion).equals(HiddenConnectionPolicy.resolveAddress(bareOnion,HiddenConnectionPolicy.Mode.TOR_ONLY,search)));
        check(HiddenConnectionPolicy.resolveAddress(bareOnion,HiddenConnectionPolicy.Mode.VPN_ONLY,search)==null);
        check("https://example.org:443/path".equals(HiddenConnectionPolicy.resolveAddress("example.org:443/path",HiddenConnectionPolicy.Mode.VPN_ONLY,search)));
        check(HiddenConnectionPolicy.resolveAddress("127.0.0.1:443/path",HiddenConnectionPolicy.Mode.VPN_ONLY,search)==null);
        check(HiddenConnectionPolicy.resolveAddress("user:secret@example.org/path",HiddenConnectionPolicy.Mode.VPN_ONLY,search)==null);
        String portOnion="a".repeat(56)+".onion:80/path";
        check(("http://"+portOnion).equals(HiddenConnectionPolicy.resolveAddress(portOnion,HiddenConnectionPolicy.Mode.TOR_ONLY,search)));
        check(HiddenConnectionPolicy.resolveAddress(portOnion,HiddenConnectionPolicy.Mode.VPN_ONLY,search)==null);
        check(searches[0]==0);
        check(HiddenConnectionPolicy.resolveAddress("ordinary plain query",HiddenConnectionPolicy.Mode.VPN_ONLY,search)!=null);
        check(searches[0]==1);
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
        check(HiddenVpnRetirement.knownEmptyTask(true,0,false,null,null)); // Actual finished child Task265 shape.
        check(!HiddenVpnRetirement.knownEmptyTask(false,0,false,null,null));
        check(!HiddenVpnRetirement.knownEmptyTask(true,-1,false,null,null));
        check(!HiddenVpnRetirement.knownEmptyTask(true,1,false,null,null));
        check(!HiddenVpnRetirement.knownEmptyTask(true,0,true,null,null));
        check(!HiddenVpnRetirement.knownEmptyTask(true,0,false,"Child",null));
        check(!HiddenVpnRetirement.knownEmptyTask(true,0,false,null,"Child"));
        check(!HiddenVpnRetirement.knownEmptyTask(true,0,false,"Main","Main"));
        check(!HiddenVpnRetirement.removableTask(false,-1,true,null,null,null,"Child")); // Empty shell does not authorize removal.
        HiddenVpnRetirement emptyWithProcess=new HiddenVpnRetirement(5000);
        check(emptyWithProcess.observe(0,true,true,true)==HiddenVpnRetirement.Decision.SWEEP);
        check(emptyWithProcess.observe(1000,true,true,true)==HiddenVpnRetirement.Decision.SWEEP);
        check(emptyWithProcess.observe(1100,true,true,false)==HiddenVpnRetirement.Decision.SWEEP);
        check(emptyWithProcess.observe(2100,true,true,false)==HiddenVpnRetirement.Decision.RELEASE);
        check(HiddenVpnRetirement.removableTask(true,0,false,null,null,"Child","Child"));
        check(HiddenVpnRetirement.pendingTask(true,0,false,null,null,"Child","Child"));
        check(HiddenVpnRetirement.knownTask(true,0,false,null,null,"Main"));
        check(!HiddenVpnRetirement.pendingTask(true,0,false,null,null,"Main","Child"));
        check(!HiddenVpnRetirement.removableTask(true,0,false,null,null,"Main","Child"));
        check(!HiddenVpnRetirement.removableTask(true,2,true,"Main","Child","Main","Child"));
        check(HiddenVpnRetirement.pendingTask(true,2,true,"Main","Child","Main","Child"));
        check(!HiddenVpnRetirement.knownTask(false,0,false,null,null,"Main"));
        check(!HiddenVpnRetirement.knownTask(true,0,false,null,null,null));
        check(!HiddenVpnRetirement.knownTask(true,1,false,null,null,"Main"));
        check(!HiddenVpnRetirement.knownTask(true,0,true,null,null,"Main"));
        check(!HiddenVpnRetirement.knownTask(true,0,false,"Main",null,"Main"));
        check(!HiddenVpnRetirement.knownTask(true,1,true,"Main",null,"Main"));
        check(!HiddenVpnRetirement.knownTask(true,0,false,"Main","Main","Main"));
        check(HiddenVpnRetirement.knownTask(true,1,true,"Main","Main","Main"));
        check(HiddenVpnRetirement.pendingTask(true,0,false,null,null,null,"Child"));
        check(!HiddenVpnRetirement.removableTask(true,1,true,"Main","Main","Main","Child"));
        check(HiddenVpnRetirement.removableTask(true,1,true,"Child","Child","Child","Child"));
        check(HiddenVpnRetirement.pendingTask(true,2,true,"Main","Child","Main","Child"));
        check(HiddenVpnRetirement.pendingTask(false,-1,true,null,null,null,"Child"));
        check(HiddenVpnRetirement.pendingTask(true,1,true,null,null,"Main","Child"));
        check(HiddenVpnRetirement.pendingTask(true,1,true,"Main",null,"Main","Child"));
        check(!HiddenVpnRetirement.pendingTask(true,1,true,"Main","Main","Main","Child"));
        System.out.println("Hidden browser/diagnostics policy: "+checks+" checks passed");
    }
}
