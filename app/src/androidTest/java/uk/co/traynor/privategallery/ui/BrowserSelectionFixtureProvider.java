package uk.co.traynor.privategallery.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Only in the separate test APK/UID; no owner gallery, production provider or network access. */
public final class BrowserSelectionFixtureProvider extends ContentProvider {
    private static final String TARGET="uk.co.traynor.privategallery.hiddenprototype";
    private static final String AUTHORITY="uk.co.traynor.privategallery.test.browser-selection-fixture";
    private final ConcurrentHashMap<String,File> files=new ConcurrentHashMap<>();
    private final java.util.Set<String> slow=ConcurrentHashMap.newKeySet();
    private void caller(){
        try{if(Binder.getCallingUid()!=getContext().getPackageManager().getApplicationInfo(TARGET,0).uid)throw new SecurityException("Synthetic fixture caller");}
        catch(android.content.pm.PackageManager.NameNotFoundException missing){throw new SecurityException(missing);}
    }
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        caller();long identity=Binder.clearCallingIdentity();
        try {
            if("grant".equals(method)) {
                String token=UUID.randomUUID().toString();File file=new File(getContext().getCacheDir(),"browser-selection-"+token+".png");
                byte[] bytes=android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a0uoAAAAASUVORK5CYII=",android.util.Base64.DEFAULT);
                try(FileOutputStream output=new FileOutputStream(file)){output.write(bytes);}
                files.put(token,file);if("slow".equals(arg))slow.add(token);Uri uri=Uri.parse("content://"+AUTHORITY+"/"+token);
                getContext().grantUriPermission(TARGET,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Bundle result=new Bundle();result.putParcelable("uri",uri);return result;
            }
            if("revoke".equals(method)) {
                Uri uri=extras.getParcelable("uri",Uri.class);
                if(uri==null || !AUTHORITY.equals(uri.getAuthority()))throw new SecurityException();
                getContext().revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                slow.remove(uri.getLastPathSegment());File file=files.remove(uri.getLastPathSegment());if(file!=null)file.delete();return Bundle.EMPTY;
            }
            throw new IllegalArgumentException("Unknown synthetic fixture operation");
        }catch(java.io.IOException error){throw new IllegalStateException(error);}finally{Binder.restoreCallingIdentity(identity);}
    }
    private File file(Uri uri)throws FileNotFoundException{File file=files.get(uri.getLastPathSegment());if(file==null)throw new FileNotFoundException();return file;}
    @Override public String getType(Uri uri){caller();if(slow.contains(uri.getLastPathSegment()))try{Thread.sleep(1000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}return "image/png";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        caller();try{File file=file(uri);MatrixCursor cursor=new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE});cursor.addRow(new Object[]{"Synthetic selected.png",file.length()});return cursor;}catch(FileNotFoundException missing){return null;}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {caller();if(!"r".equals(mode))throw new SecurityException("Read only");return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);}
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
