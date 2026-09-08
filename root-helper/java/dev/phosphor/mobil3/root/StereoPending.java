package dev.phosphor.mobil3.root;

/** One ordered block. Read-frame and monitor-frame zero share the same origin. */
public final class StereoPending {
    public static double rate(long firstFrame,long firstNs,long lastFrame,long lastNs) {
        if(firstFrame<0 || lastFrame<=firstFrame || firstNs<0 || lastNs-firstNs<1000000000L)throw new IllegalArgumentException("timestamp_progress_bound");
        return (lastFrame-firstFrame)*1e9/(lastNs-firstNs);
    }
    public int count, offset;
    public long readFrames, writtenFrames, queueHighwater;
    public void read(int samples) {
        if(offset!=count || samples<0 || samples>960 || (samples&1)!=0)throw new IllegalArgumentException("pending_read_contract");
        count=samples;offset=0;readFrames+=samples/2;
    }
    public void wrote(int samples) {
        if(samples<0 || samples>count-offset || (samples&1)!=0)throw new IllegalArgumentException("pending_write_contract");
        offset+=samples;writtenFrames+=samples/2;
    }
    public boolean pending(){return offset<count;}
    public long queue(long head) {
        long queued=writtenFrames-head;
        if(head<0 || queued<0 || queued>4800)throw new IllegalArgumentException("monitor_queue_overrun");
        queueHighwater=Math.max(queueHighwater,queued);return queued;
    }
}
