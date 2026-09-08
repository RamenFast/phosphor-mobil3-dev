package dev.phosphor.mobil3.root;

/** One ordered block. Read-frame and monitor-frame zero share the same origin. */
public final class StereoPending {
    public static final int BUFFER_FRAMES=960, START_FRAMES=480, QUEUE_LIMIT=4800;
    public static void monitorBounds(int capacity,int size,int threshold,int sizeResult,int thresholdResult) {
        if(capacity<1 || size<1 || size>capacity || size>BUFFER_FRAMES || threshold<1 || threshold>size ||
            sizeResult!=size || thresholdResult!=threshold)throw new IllegalArgumentException("monitor_buffer_configuration");
    }
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
        if(head<0 || head>writtenFrames)throw new IllegalArgumentException("monitor_queue_overrun");
        long queued=writtenFrames-head;
        queueHighwater=Math.max(queueHighwater,queued);
        if(queued>QUEUE_LIMIT)throw new IllegalArgumentException("monitor_queue_overrun");
        return queued;
    }
}
