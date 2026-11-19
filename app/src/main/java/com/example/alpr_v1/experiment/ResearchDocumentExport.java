package com.example.alpr_v1.experiment;

import android.content.ContentResolver;
import android.net.Uri;
import java.io.*;
import java.security.MessageDigest;
import com.example.alpr_v1.inference.RuntimeExecutionEvidence;

/** Copies a closed archive through SAF; success is reported only AFTER close and read-back. */
public final class ResearchDocumentExport {
    public interface Progress {void update(String stage,long done,long total);}
    public static final class Receipt {
        public final long bytes;public final String sha256,verification;
        Receipt(long bytes,String sha,String status){this.bytes=bytes;sha256=sha;verification=status;}
    }
    private ResearchDocumentExport() { }
    public static String displayName(ContentResolver resolver,Uri uri){
        try(android.database.Cursor cursor=resolver.query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))return cursor.getString(0);
        }catch(Exception unavailable){/* URI remains the authoritative document identifier. */}
        return "Nazwa niedostępna u dostawcy dokumentu";
    }
    public static Receipt copy(ContentResolver resolver,Uri uri,File source,Progress progress)throws Exception{
        long size=source.length(),modified=source.lastModified();
        if(!source.isFile()||size<=0)throw new IOException("SOURCE_ARCHIVE_MISSING_OR_EMPTY");
        String expected=RuntimeExecutionEvidence.sha256(source);MessageDigest digest=MessageDigest.getInstance("SHA-256");long written=0,last=0;
        progress.update("COPYING_DOCUMENT",0,size);
        try(InputStream input=new FileInputStream(source);OutputStream output=resolver.openOutputStream(uri,"w")){
            if(output==null)throw new IOException("DOCUMENT_OUTPUT_UNAVAILABLE: "+uri);
            byte[] buffer=new byte[65536];int n;
            while((n=input.read(buffer))!=-1){output.write(buffer,0,n);digest.update(buffer,0,n);written+=n;
                if(written-last>=4L*1024*1024){progress.update("COPYING_DOCUMENT",written,size);last=written;}}
            output.flush();
        }
        String actual=hex(digest.digest());
        if(written!=size||source.length()!=size||source.lastModified()!=modified||!expected.equals(actual))throw new IOException("SOURCE_ARCHIVE_CHANGED_DURING_COPY");
        progress.update("VERIFYING_DOCUMENT",0,size);
        long read=0;MessageDigest verify=MessageDigest.getInstance("SHA-256");
        // A provider may permit writing without read-back. Retain the copy, but never call it verified.
        InputStream opened;
        try{opened=resolver.openInputStream(uri);}catch(IOException|SecurityException unavailable){return new Receipt(written,actual,"SAVED_UNVERIFIED: "+unavailable);}
        if(opened==null)return new Receipt(written,actual,"SAVED_UNVERIFIED: DOCUMENT_READ_UNAVAILABLE");
        try(InputStream input=opened){byte[] b=new byte[65536];int n;last=0;
            while((n=input.read(b))!=-1){verify.update(b,0,n);read+=n;if(read-last>=4L*1024*1024){progress.update("VERIFYING_DOCUMENT",read,size);last=read;}}}
        if(read!=size||!actual.equals(hex(verify.digest())))throw new IOException("DOCUMENT_READBACK_MISMATCH: bytes="+read+" expected="+size);
        progress.update("VERIFYING_DOCUMENT",read,size);return new Receipt(written,actual,"VERIFIED_SHA256");
    }
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return s.toString();}
}
