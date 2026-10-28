package uk.co.traynor.privategallery.core.browser.v2;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable host checks of the actual production reservation/once-owner implementation. */
public final class BrowserPermissionResultGateChecks {
    private static void check(boolean condition){if(!condition)throw new AssertionError();}
    private static BrowserExternalResultGate.Owner<Map<String,Boolean>> owner(AtomicInteger grants,AtomicInteger denies){return new BrowserExternalResultGate.Owner<Map<String,Boolean>>(result->{if(Boolean.TRUE.equals(result.get("camera")))grants.incrementAndGet();else denies.incrementAndGet();},denies::incrementAndGet);}
    public static void main(String[] args)throws Exception {
        for(String kind:List.of("camera","microphone","location")) {
            BrowserExternalResultGate<Map<String,Boolean>> gate=new BrowserExternalResultGate<>();AtomicInteger a=new AtomicInteger(),ad=new AtomicInteger(),b=new AtomicInteger(),bd=new AtomicInteger();
            var first=owner(a,ad);var replacement=owner(b,bd);
            check(gate.reserve(first));first.cancel(); // Navigation/replacement/background/UI disposal.
            check(gate.busy()&&!gate.reserve(replacement));
            gate.result(Map.of("camera",true));
            check(a.get()==0&&ad.get()==1&&b.get()==0&&bd.get()==0&&!gate.busy());
            check(gate.reserve(replacement));gate.result(Map.of("camera",true));gate.result(Map.of("camera",true));
            check(b.get()==1&&bd.get()==0);first.finish(Map.of("camera",true));first.cancel();check(ad.get()==1);
        }
        BrowserExternalResultGate<Map<String,Boolean>> gate=new BrowserExternalResultGate<>();AtomicInteger a=new AtomicInteger(),d=new AtomicInteger();var first=owner(a,d);
        check(gate.reserve(first));gate.launchFailed(first);check(!gate.busy()&&d.get()==1);gate.result(Map.of("camera",true));check(a.get()==0);
        var second=owner(a,d);check(gate.reserve(second));gate.clear();check(!gate.busy()&&d.get()==2);gate.result(Map.of("camera",true));check(a.get()==0);
        // Reentrant completion may reserve B, but a late failure belonging to A cannot erase B.
        BrowserExternalResultGate<Map<String,Boolean>> recursive=new BrowserExternalResultGate<>();AtomicInteger b=new AtomicInteger(),bd=new AtomicInteger();var replacement=owner(b,bd);
        var original=new BrowserExternalResultGate.Owner<Map<String,Boolean>>(result->check(recursive.reserve(replacement)),()->{});
        check(recursive.reserve(original));recursive.result(Map.of());recursive.launchFailed(original);check(recursive.busy());recursive.result(Map.of("camera",true));check(b.get()==1);
        // Callback references must be released even while an OS reservation marker remains.
        var canceled=owner(new AtomicInteger(),new AtomicInteger());check(gate.reserve(canceled));canceled.cancel();
        for(String field:List.of("completion","rejection")){var f=canceled.getClass().getDeclaredField(field);f.setAccessible(true);check(f.get(canceled)==null);}check(gate.busy());gate.result(Map.of());
        for(int iteration=0;iteration<200;iteration++) {
            AtomicInteger grants=new AtomicInteger(),denied=new AtomicInteger();var raced=owner(grants,denied);var concurrent=new BrowserExternalResultGate<Map<String,Boolean>>();check(concurrent.reserve(raced));
            Thread result=new Thread(()->concurrent.result(Map.of("camera",true)));Thread cancel=new Thread(raced::cancel);result.start();cancel.start();result.join();cancel.join();check(grants.get()+denied.get()==1);
        }

        BrowserExternalResultGate<List<String>> files=new BrowserExternalResultGate<>();AtomicInteger fileA=new AtomicInteger(),fileB=new AtomicInteger(),fileCancel=new AtomicInteger();
        var single=new BrowserExternalResultGate.Owner<List<String>>(uris->fileA.incrementAndGet(),fileCancel::incrementAndGet);
        var multi=new BrowserExternalResultGate.Owner<List<String>>(uris->fileB.incrementAndGet(),()->{});
        check(files.reserve(single,"single"));single.cancel();check(!files.reserve(multi,"multiple"));
        files.result("multiple",List.of("wrong-channel"));check(files.busy());
        files.result("single",List.of("content://selected-by-A"));check(!files.busy()&&fileA.get()==0&&fileB.get()==0&&fileCancel.get()==1);
        check(files.reserve(multi,"multiple"));files.result("single",List.of("late-cross-channel"));check(files.busy()&&fileB.get()==0);
        files.result("multiple",List.of("content://explicitly-selected-by-B"));check(fileB.get()==1);
        check(BrowserDeviceUriPolicy.rejection("file",null,null,100,true)!=null);
        check(BrowserDeviceUriPolicy.rejection("data",null,null,100,true)!=null);
        check(BrowserDeviceUriPolicy.rejection("https","public.example",200,100,true)!=null);
        check(BrowserDeviceUriPolicy.rejection("content","same.uid.alias",100,100,true)!=null);
        check(BrowserDeviceUriPolicy.rejection("content","10@media",200,100,true)!=null);
        check(BrowserDeviceUriPolicy.rejection("content","external",200,100,false)!=null);
        check(BrowserDeviceUriPolicy.rejection("content","external",null,100,true)!=null);
        check(BrowserDeviceUriPolicy.rejection("content","media",200,100,true)==null);
        System.out.println("PASS browser OS permission ownership: replacement, cancelled markers, once completion, reentrant launch, callback release and result/cancel races");
    }
}
