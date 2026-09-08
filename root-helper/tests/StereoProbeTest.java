package dev.phosphor.mobil3.root;
import java.util.Arrays;

public final class StereoProbeTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("accepted");}catch(IllegalArgumentException expected){checks++;}}
    private static StereoStats stats(int kind,int chunk) {
        StereoStats s=new StereoStats();short[] b=new short[chunk*2];
        for(int frame=0;frame<240000;) {
            int count=Math.min(chunk,240000-frame);
            for(int i=0;i<count;i++) {
                double l=Math.sin(2*Math.PI*997*(frame+i)/48000)*819,r=Math.sin(2*Math.PI*1499*(frame+i)/48000)*819;
                if(kind==1){double swap=l;l=r;r=swap;}
                if(kind==2)r=l;
                if(kind==3){l=0;r=0;}
                if(kind==4){double orig=l;l+=r*0.1;r+=orig*0.1;}
                if(kind==5){l=32767;r=-32768;}
                b[i*2]=(short)l;b[i*2+1]=(short)r;
            }
            s.add(b,count*2);frame+=count;
        }
        return s;
    }
    public static void main(String[] args)throws Exception {
        StereoStats whole=stats(0,480);
        check(whole.stereo() && Math.abs(whole.phase(0,0))<0.001 && Math.abs(whole.phase(1,1))<0.001);check(whole.frames==240000);check(whole.measured==144000);
        check(whole.separation(0)>60 && whole.separation(1)>60);check(Math.abs(whole.correlation())<0.001);
        for(int kind=1;kind<=5;kind++)check(!stats(kind,137).stereo());
        for(int chunk:new int[]{1,13,137,479,480,997}) {
            StereoStats partial=stats(0,chunk);check(partial.stereo());check(partial.rms(0)==whole.rms(0));check(partial.power(1,1)==whole.power(1,1) && partial.phase(1,1)==whole.phase(1,1));
        }
        rejects(()->whole.add(new short[3],3));rejects(()->whole.add(new short[2],-2));
        StereoPending pending=new StereoPending();pending.read(960);pending.wrote(2);check(pending.offset==2 && pending.writtenFrames==1);
        rejects(()->pending.read(2));rejects(()->pending.wrote(1));rejects(()->pending.wrote(960));
        pending.wrote(0);check(pending.offset==2);pending.wrote(958);check(!pending.pending());check(pending.readFrames==pending.writtenFrames);
        check(pending.queue(0)==480);check(pending.queueHighwater==480);pending.read(4);pending.wrote(4);check(pending.writtenFrames==482);
        rejects(()->pending.queue(483));rejects(()->pending.read(962));
        StereoPending.monitorBounds(8793,960,480,960,480);check(true);
        StereoPending.monitorBounds(8793,960,960,960,960);check(true);
        StereoPending.monitorBounds(512,512,480,512,480);check(true);
        rejects(()->StereoPending.monitorBounds(8793,8793,8793,8793,8793));
        rejects(()->StereoPending.monitorBounds(8793,961,480,961,480));
        rejects(()->StereoPending.monitorBounds(8793,960,961,960,961));
        rejects(()->StereoPending.monitorBounds(8793,960,0,960,0));
        rejects(()->StereoPending.monitorBounds(8793,960,480,-3,480));
        rejects(()->StereoPending.monitorBounds(8793,960,480,960,-2));
        rejects(()->StereoPending.monitorBounds(8793,960,480,512,480));
        rejects(()->StereoPending.monitorBounds(8793,960,480,960,960));
        rejects(()->StereoPending.monitorBounds(959,960,480,960,480));
        rejects(()->StereoPending.monitorBounds(0,0,0,0,0));
        StereoPending queue=new StereoPending();
        for(int i=0;i<10;i++){queue.read(960);queue.wrote(960);}
        check(queue.queue(0)==4800);queue.read(384);queue.wrote(384);
        rejects(()->queue.queue(0));check(queue.queueHighwater==4992);
        check(queue.queue(192)==4800);rejects(()->queue.queue(-1));
        String build="a".repeat(64);
        for(int mode:new int[]{4,5}) {
            byte[] id=Protocol.identity(10401,build,7,mode);check(Protocol.mode(id)==mode);
            check(Protocol.validateInit(Protocol.read(new java.io.ByteArrayInputStream(Protocol.encode(10,id))),build)==10401);
            try{Protocol.pcm(id,0,new RootEpoch.Binding(1,0),new short[1],1);throw new AssertionError("probe PCM accepted");}catch(java.io.IOException expected){check(true);}
        }
        // A short prior read cannot allow the next finite-mode3 read to cross its cap.
        for(long frames:new long[]{79841,79900,79999,80000})check(Protocol.readCount(3,160,frames)==Math.min(160,80000-frames));
        check(Protocol.readCount(2,160,79999)==160);check(Protocol.readCount(0,137,79999)==137);
        check(StereoPending.rate(48000,1000000000L,192000,4000000000L)==48000);
        rejects(()->StereoPending.rate(48000,1000000000L,48000,4000000000L));
        rejects(()->StereoPending.rate(0,1000000000L,4800,1100000000L));
        System.out.println("Stereo host checks passed: "+checks);
    }
}
