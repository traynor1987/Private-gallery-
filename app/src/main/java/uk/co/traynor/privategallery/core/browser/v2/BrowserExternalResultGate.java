package uk.co.traynor.privategallery.core.browser.v2;

import java.util.function.Consumer;

/** One OS launcher reservation. Cancellation releases callbacks, never reassigns a late result. */
public final class BrowserExternalResultGate<T> {
    public static final class Owner<T> {
        private Consumer<T> completion;
        private Runnable rejection;
        private boolean finished;
        public Owner(Consumer<T> completion,Runnable rejection) {
            if(completion==null || rejection==null)throw new NullPointerException();
            this.completion=completion;this.rejection=rejection;
        }
        public synchronized boolean pending(){return !finished;}
        public void finish(T result) {
            Consumer<T> work;
            synchronized(this){if(finished)return;finished=true;work=completion;completion=null;rejection=null;}
            work.accept(result);
        }
        public void cancel(){
            Runnable work;
            synchronized(this){if(finished)return;finished=true;work=rejection;completion=null;rejection=null;}
            work.run();
        }
    }
    private Owner<T> reservation;
    private String channel;
    public synchronized boolean reserve(Owner<T> owner){return reserve(owner,"");}
    public synchronized boolean reserve(Owner<T> owner,String source){
        if(reservation!=null || !owner.pending())return false;
        reservation=owner;channel=source;return true;
    }
    public synchronized boolean busy(){return reservation!=null;}
    public void result(T result){result("",result);}
    public void result(String source,T result){
        Owner<T> captured;
        synchronized(this){if(reservation==null || !java.util.Objects.equals(channel,source))return;captured=reservation;reservation=null;channel=null;}
        captured.finish(result);
    }
    /** Only use when launcher.launch itself threw: no Android result can now own this slot. */
    public void launchFailed(Owner<T> owner){
        synchronized(this){if(reservation==owner){reservation=null;channel=null;}}
        owner.cancel();
    }
    /** ViewModel destruction may discard the marker; ordinary UI disposal must only cancel Owner. */
    public void clear(){
        Owner<T> captured;
        synchronized(this){captured=reservation;reservation=null;channel=null;}
        if(captured!=null)captured.cancel();
    }
}
