package dev.phosphor.mobil3.root;

/** Streaming aggregates only. Frame zero is the first post-START captured frame. */
public final class StereoStats {
    public static final int RATE=48000, WINDOW=144000, LIMIT=264000;
    public int frames, measured, reads, zeroReads;
    public double peak;
    private double ll, rr, lr, leftSum, rightSum;
    private final double[][] re=new double[2][2], im=new double[2][2];
    public void add(short[] samples,int count) {
        if(count<0 || count>samples.length || (count&1)!=0 || frames+count/2>LIMIT) throw new IllegalArgumentException("stereo_count_or_limit");
        reads++; if(count==0)zeroReads++;
        for(int i=0;i<count;i+=2,frames++) {
            double l=samples[i]/32768.0,r=samples[i+1]/32768.0;
            peak=Math.max(peak,Math.max(Math.abs(l),Math.abs(r)));
            if(frames<RATE || frames>=RATE+WINDOW)continue;
            ll+=l*l;rr+=r*r;lr+=l*r;leftSum+=l;rightSum+=r;
            double w=0.5-0.5*Math.cos(2*Math.PI*measured/(WINDOW-1));
            for(int f=0;f<2;f++) {
                double phase=2*Math.PI*(f==0?997:1499)*measured/RATE;
                double c=w*Math.cos(phase),s=w*Math.sin(phase);
                re[0][f]+=l*c;im[0][f]+=l*s;re[1][f]+=r*c;im[1][f]+=r*s;
            }
            measured++;
        }
    }
    public double rms(int channel){return measured==0?0:Math.sqrt((channel==0?ll:rr)/measured);}
    public double power(int channel,int tone){return (re[channel][tone]*re[channel][tone]+im[channel][tone]*im[channel][tone])/(WINDOW*(double)WINDOW);}
    public double phase(int channel,int tone){return Math.atan2(re[channel][tone],im[channel][tone]);}
    public double separation(int tone){return 10*Math.log10(Math.max(1e-30,power(tone,tone))/Math.max(1e-30,power(1-tone,tone)));}
    public double correlation(){double d=Math.sqrt(Math.max(0,ll-leftSum*leftSum/Math.max(1,measured))*Math.max(0,rr-rightSum*rightSum/Math.max(1,measured)));return d==0?1:(lr-leftSum*rightSum/Math.max(1,measured))/d;}
    public boolean stereo(){return measured==WINDOW && rms(0)>0.001 && rms(1)>0.001 && peak<32767/32768.0 && Math.abs(correlation())<0.1 && separation(0)>=30 && separation(1)>=30;}
}
