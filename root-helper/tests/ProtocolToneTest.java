package dev.phosphor.mobil3.root;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public final class ProtocolToneTest {
    private static int checks;
    private static void check(boolean v) { checks++; if(!v)throw new AssertionError("check "+checks); }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action, String cause) throws Exception {
        try { action.run(); throw new AssertionError("accepted "+cause); }
        catch(IOException | IllegalArgumentException expected) { check(expected.getMessage().contains(cause)); }
    }
    public static void main(String[] args) throws Exception {
        String build="a".repeat(64);byte[] id=Protocol.identity(10401,build,1,0);
        byte[] wire=Protocol.encode(10,id);
        InputStream partial=new ByteArrayInputStream(wire){@Override public synchronized int read(byte[] b,int off,int len){return super.read(b,off,Math.min(1,len));}};
        check(Protocol.validateInit(Protocol.read(partial),build)==10401);
        rejects(()->Protocol.validateInit(Protocol.read(new ByteArrayInputStream(wire)),"b".repeat(64)),"mismatch");
        rejects(()->Protocol.identity(0,build,1,0),"identity");
        rejects(()->Protocol.identity(10401,"../x",1,0),"identity");
        rejects(()->Protocol.encode(11,new byte[4097]),"overflow");
        rejects(()->Protocol.read(new ByteArrayInputStream(Arrays.copyOf(wire,wire.length-1))),"eof");
        byte[] bad=wire.clone();bad[0]=0;rejects(()->Protocol.read(new ByteArrayInputStream(bad)),"magic");
        rejects(()->Protocol.tagged(id,"x".repeat(4096)),"limit");
        check(Protocol.read(new ByteArrayInputStream(Protocol.encode(3,new byte[0]))).kind==3);
        try { Protocol.read(new InputStream(){public int read(){return 0;}public int read(byte[] b,int o,int n) throws IOException {throw new IOException("read cause retained");}}); throw new AssertionError(); } catch(IOException expected) {check(expected.getMessage().equals("read cause retained"));}
        check(id.length==80 && Protocol.generation(id)==1 && Protocol.mode(id)==0);
        rejects(()->Protocol.identity(10401,build,0,2),"identity");
        rejects(()->Protocol.identity(10401,build,1,6),"identity");
        byte[] stream=Protocol.identity(10401,build,42,2);
        byte[] pcm=Protocol.pcm(stream,0,new short[]{-32768,0,32767},3);
        check(pcm.length==110);
        java.nio.ByteBuffer payload=java.nio.ByteBuffer.wrap(pcm).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        check(payload.getLong(80)==0 && payload.getInt(88)==16000 && payload.getInt(92)==1 && payload.getInt(96)==2 && payload.getInt(100)==3);
        check(payload.getShort(104)==-32768 && payload.getShort(108)==32767);
        rejects(()->Protocol.pcm(stream,0,new short[161],161),"pcm");
        rejects(()->Protocol.pcm(stream,-1,new short[1],1),"pcm");
        rejects(()->Protocol.pcm(stream,0,new short[1],0),"pcm");
        byte[] ctrl=java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(42).array();
        Protocol.control(Protocol.read(new ByteArrayInputStream(Protocol.encode(3,ctrl))),42);check(true);
        rejects(()->Protocol.control(Protocol.read(new ByteArrayInputStream(Protocol.encode(3,ctrl))),41),"generation");
        check(Protocol.progress(stream,9).length==88);
        for(int frequency : new int[]{997,440,0}) {
            ToneStats stats=new ToneStats();short[] block=new short[137];
            while(stats.frames<ToneStats.LIMIT) {int count=Math.min(block.length,ToneStats.LIMIT-stats.frames);for(int i=0;i<count;i++)block[i]=(short)(Math.sin(2*Math.PI*frequency*(stats.frames+i)/16000)*819);stats.add(block,count);}
            check(stats.frames==80000);check(stats.tonePresent()==(frequency==997));
            if(frequency==997){check(Math.abs(stats.frequency()-997)<1);check(stats.toneRatio()>0.99);check(stats.rms()>0.017 && stats.rms()<0.018);}
            if(frequency==0){check(stats.nonzero==0);check(stats.rms()==0);}
            rejects(()->stats.add(new short[1],1),"limit");
        }
        ToneStats empty=new ToneStats();empty.add(new short[1],0);check(empty.zeroReads==1 && !empty.tonePresent());
        ToneStats leading=new ToneStats();leading.add(new short[8000],8000);
        short[] tone=new short[72000];for(int i=0;i<tone.length;i++)tone[i]=(short)(Math.sin(2*Math.PI*997*i/16000)*819);
        leading.add(tone,tone.length);check(leading.tonePresent());check(Math.abs(leading.frequency()-997)<1);check(leading.frames==80000 && leading.nonzero<72000);
        rejects(()->empty.add(new short[1],-1),"limit");
        short[] mono=new short[160];for(int i=0;i<mono.length;i++)mono[i]=(short)(Math.sin(i*0.17)*32767);
        float[] whole=new dev.phosphor.mobil3.RootPcmNormalizer().convert(mono);
        check(whole.length==mono.length*6);
        for(int split=1;split<mono.length;split++) {
            dev.phosphor.mobil3.RootPcmNormalizer n=new dev.phosphor.mobil3.RootPcmNormalizer();
            float[] left=n.convert(Arrays.copyOfRange(mono,0,split));
            float[] right=n.convert(Arrays.copyOfRange(mono,split,mono.length));
            float[] joined=Arrays.copyOf(left,left.length+right.length);System.arraycopy(right,0,joined,left.length,right.length);
            check(Arrays.equals(whole,joined));
        }
        boolean duplicated=true,bounded=true;for(int i=0;i<whole.length;i+=2){duplicated&=whole[i]==whole[i+1];bounded&=whole[i]>=-1 && whole[i]<=1;}
        check(duplicated);check(bounded);
        dev.phosphor.mobil3.RootPcmNormalizer continuous=new dev.phosphor.mobil3.RootPcmNormalizer();
        int count=0;for(int i=0;i<500;i++)count+=continuous.convert(new short[160]).length/2;
        check(count==240000);
        float[] limits=new dev.phosphor.mobil3.RootPcmNormalizer().convert(new short[]{-32768,32767});
        check(limits[0]==-1f && limits[1]==-1f && limits.length==12);
        rejects(()->new dev.phosphor.mobil3.RootPcmNormalizer().convert(new short[0]),"pcm_count");
        rejects(()->new dev.phosphor.mobil3.RootPcmNormalizer().convert(new short[161]),"pcm_count");
        System.out.println("Protocol/tone host checks passed: "+checks);
    }
    private ProtocolToneTest() {}
}
