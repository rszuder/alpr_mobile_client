package com.example.alpr_v1.experiment;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Disposable, derived join index. Primary evidence stays in the session JSONL journals. */
final class ExternalJsonSort implements AutoCloseable {
    static final int BUFFER_BYTES=4*1024*1024, FAN_IN=16, MAX_LINE_CHARS=32*1024*1024;
    interface Row { void accept(String key,JSONObject value)throws Exception; }
    private final File root;
    private final List<String> buffer=new ArrayList<>();
    private int bytes,sequence,runs;
    ExternalJsonSort(File parent)throws IOException {root=Files.createTempDirectory(parent.toPath(),"f6-sort-").toFile();}
    void add(String key,JSONObject value)throws Exception {
        if(key.indexOf('\t')>=0||key.indexOf('\n')>=0)throw new IOException("Invalid join key");
        String line=key+'\t'+value.toString();
        if(line.length()>MAX_LINE_CHARS)throw new IOException("F6 record too large");
        if(bytes+2L*line.length()>BUFFER_BYTES&&!buffer.isEmpty())spill();
        buffer.add(line);bytes+=2*line.length();
        if(bytes>=BUFFER_BYTES)spill();
    }
    private File file(int generation,int index){return new File(root,generation+"-"+index+".jsonl");}
    private void spill()throws IOException {
        if(buffer.isEmpty())return;Collections.sort(buffer);
        try(BufferedWriter out=writer(file(sequence,runs++))){for(String s:buffer){out.write(s);out.newLine();}}
        buffer.clear();bytes=0;
    }
    void each(Row row)throws Exception {
        spill();int generation=sequence,count=runs;
        while(count>1){int next=0;
            for(int begin=0;begin<count;begin+=FAN_IN){
                int end=Math.min(count,begin+FAN_IN);
                try(BufferedWriter out=writer(file(generation+1,next++))){merge(generation,begin,end,(key,value)->{out.write(key);out.write('\t');out.write(value.toString());out.newLine();});}
                for(int n=begin;n<end;n++)Files.delete(file(generation,n).toPath());
            }count=next;generation++;
        }
        if(count==1)merge(generation,0,1,row);
    }
    private void merge(int generation,int begin,int end,Row row)throws Exception {
        List<BufferedReader> readers=new ArrayList<>();
        class Head {int reader;String line;Head(int r,String s){reader=r;line=s;}}
        PriorityQueue<Head> heap=new PriorityQueue<>(Comparator.comparing(h->h.line));
        try {
            for(int n=begin;n<end;n++){BufferedReader r=Files.newBufferedReader(file(generation,n).toPath(),StandardCharsets.UTF_8);readers.add(r);String s=line(r);if(s!=null)heap.add(new Head(readers.size()-1,s));}
            while(!heap.isEmpty()){Head h=heap.remove();int split=h.line.indexOf('\t');
                row.accept(h.line.substring(0,split),new JSONObject(h.line.substring(split+1)));
                String s=line(readers.get(h.reader));if(s!=null)heap.add(new Head(h.reader,s));}
        }finally{for(BufferedReader r:readers)r.close();}
    }
    static BufferedWriter writer(File file)throws IOException{return Files.newBufferedWriter(file.toPath(),StandardCharsets.UTF_8);}
    static String line(BufferedReader in)throws IOException {
        StringBuilder s=new StringBuilder();int c;
        while((c=in.read())!=-1&&c!='\n'){if(s.length()>=MAX_LINE_CHARS)throw new IOException("F6 line size limit");if(c!='\r')s.append((char)c);}
        return c==-1&&s.length()==0?null:s.toString();
    }
    static void rows(File file,java.util.function.Consumer<JSONObject> row,java.util.function.Consumer<Exception> invalid)throws IOException {
        if(!file.isFile())return;
        try(BufferedReader in=Files.newBufferedReader(file.toPath(),StandardCharsets.UTF_8)){String s;
            while((s=line(in))!=null)if(!s.trim().isEmpty())try{row.accept(new JSONObject(s));}catch(Exception e){invalid.accept(e);}}
    }
    public void close()throws IOException {
        // Only this instance's freshly created directory; never recurse over session evidence.
        File[] files=root.listFiles();if(files!=null)for(File f:files)Files.deleteIfExists(f.toPath());Files.deleteIfExists(root.toPath());
    }
}
