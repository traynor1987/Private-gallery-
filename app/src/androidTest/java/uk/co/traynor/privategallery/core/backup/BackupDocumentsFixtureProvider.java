package uk.co.traynor.privategallery.core.backup;

import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Separate test APK/UID. Only disposable documents and exact debug-target control calls. */
public final class BackupDocumentsFixtureProvider extends DocumentsProvider {
    public static final String AUTHORITY="uk.co.traynor.privategallery.test.backup-documents";
    private static final String TARGET="uk.co.traynor.privategallery.hiddenprototype";
    private final ConcurrentHashMap<String,String> modes=new ConcurrentHashMap<>();
    private volatile String latest;
    private volatile String nextMode="normal";
    private final AtomicInteger opened=new AtomicInteger(),closed=new AtomicInteger(),blocked=new AtomicInteger(),cancelled=new AtomicInteger();
    private File directory(){File f=new File(getContext().getCacheDir(),"backup-documents62");f.mkdirs();return f;}
    private File file(String id)throws FileNotFoundException{if(!id.matches("[a-f0-9-]{36}"))throw new FileNotFoundException();return new File(directory(),id);}
    private void controlCaller(){try{if(Binder.getCallingUid()!=getContext().getPackageManager().getApplicationInfo(TARGET,0).uid)throw new SecurityException();}catch(android.content.pm.PackageManager.NameNotFoundException e){throw new SecurityException(e);}}
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(!method.startsWith("fixture-"))return super.call(method,arg,extras);
        controlCaller();long identity=Binder.clearCallingIdentity();try{
            Bundle out=new Bundle();
            if(method.equals("fixture-create")){
                String id=createDocument("root","application/octet-stream","Synthetic backup");
                modes.put(id,arg==null?"normal":arg);Uri uri=DocumentsContract.buildDocumentUri(AUTHORITY,id);
                getContext().grantUriPermission(TARGET,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);out.putParcelable("uri",uri);
            }else if(method.equals("fixture-mode")){modes.put(arg,extras.getString("mode"));}
            else if(method.equals("fixture-revoke")){Uri uri=DocumentsContract.buildDocumentUri(AUTHORITY,arg);getContext().revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
            else if(method.equals("fixture-delete")){file(arg).delete();modes.remove(arg);}
            else if(method.equals("fixture-tree")){Uri uri=DocumentsContract.buildTreeDocumentUri(AUTHORITY,"root");getContext().grantUriPermission(TARGET,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);out.putParcelable("uri",uri);}
            else if(method.equals("fixture-next-mode")){nextMode=arg;}
            else if(method.equals("fixture-latest")){out.putString("id",latest);}
            else if(method.equals("fixture-stats")){out.putInt("opened",opened.get());out.putInt("closed",closed.get());out.putInt("blocked",blocked.get());out.putInt("cancelled",cancelled.get());}
            else if(method.equals("fixture-reset")){for(File f:directory().listFiles())f.delete();modes.clear();opened.set(0);closed.set(0);blocked.set(0);cancelled.set(0);}
            else throw new IllegalArgumentException(method);
            return out;
        }catch(IOException e){throw new IllegalStateException(e);}finally{Binder.restoreCallingIdentity(identity);}
    }
    @Override public Cursor queryRoots(String[] projection){MatrixCursor c=new MatrixCursor(new String[]{DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.COLUMN_MIME_TYPES});c.addRow(new Object[]{"nova62","root","Nova62 synthetic documents",DocumentsContract.Root.FLAG_SUPPORTS_CREATE|DocumentsContract.Root.FLAG_LOCAL_ONLY,"*/*"});return c;}
    private void row(MatrixCursor c,String id)throws FileNotFoundException{boolean root=id.equals("root");File f=root?directory():file(id);if(!f.exists())throw new FileNotFoundException();c.addRow(new Object[]{id,root?DocumentsContract.Document.MIME_TYPE_DIR:"application/octet-stream",root?"Nova62 synthetic documents":"Synthetic backup "+id,root?DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE:DocumentsContract.Document.FLAG_SUPPORTS_WRITE|DocumentsContract.Document.FLAG_SUPPORTS_DELETE,f.length()});}
    private MatrixCursor cursor(){return new MatrixCursor(new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_SIZE});}
    @Override public Cursor queryDocument(String id,String[] projection)throws FileNotFoundException{MatrixCursor c=cursor();row(c,id);return c;}
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sort)throws FileNotFoundException{MatrixCursor c=cursor();for(File f:directory().listFiles())row(c,f.getName());return c;}
    @Override public String createDocument(String parent,String mime,String name)throws FileNotFoundException{String id=UUID.randomUUID().toString();try{if(!file(id).createNewFile())throw new IOException();}catch(IOException e){throw new FileNotFoundException(e.toString());}modes.put(id,nextMode);nextMode="normal";latest=id;return id;}
    @Override public boolean isChildDocument(String parent,String id){return parent.equals("root")&&!id.equals("root");}
    @Override public void deleteDocument(String id)throws FileNotFoundException{file(id).delete();modes.remove(id);}
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws FileNotFoundException{
        File f=file(id);if(!f.exists())throw new FileNotFoundException();String fault=modes.getOrDefault(id,"normal");boolean write=mode.contains("w");
        if(write&&fault.equals("deny-write"))throw new FileNotFoundException("Synthetic denied write");
        if((!write&&fault.equals("block-read"))||(write&&fault.equals("block-write"))){
            blocked.incrementAndGet();Object gate=new Object();if(signal!=null)signal.setOnCancelListener(()->{cancelled.incrementAndGet();synchronized(gate){gate.notifyAll();}});
            synchronized(gate){long until=SystemClock.uptimeMillis()+30000;while(signal==null||!signal.isCanceled()){long remaining=until-SystemClock.uptimeMillis();if(remaining<=0)throw new FileNotFoundException("Synthetic cancellation deadline");try{gate.wait(remaining);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new FileNotFoundException();}}}
            signal.throwIfCanceled();
        }
        try{
            if(write&&fault.equals("fail-write")){ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createReliablePipe();pipe[0].closeWithError("Synthetic destination full");return pipe[1];}
            if(!write&&fault.equals("close-error")){ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createReliablePipe();pipe[1].closeWithError("Synthetic provider close failure");return pipe[0];}
            if(!write&&fault.equals("truncate")){try(RandomAccessFile a=new RandomAccessFile(f,"rw")){a.setLength(Math.max(0,a.length()-17));}}
            if(!write&&fault.equals("tamper")){try(RandomAccessFile a=new RandomAccessFile(f,"rw")){if(a.length()>0){a.seek(a.length()-1);int b=a.read();a.seek(a.length()-1);a.write(b^1);}}}
            opened.incrementAndGet();return ParcelFileDescriptor.open(f,ParcelFileDescriptor.parseMode(mode),new Handler(Looper.getMainLooper()),error->closed.incrementAndGet());
        }catch(IOException e){throw new FileNotFoundException(e.toString());}
    }
}
