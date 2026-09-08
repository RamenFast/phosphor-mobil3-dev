package dev.phosphor.mobil3.root;

import android.content.Context;
import android.media.*;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import org.json.JSONObject;
import static dev.phosphor.mobil3.root.AudioPolicyMain.*;

/** Fixed debug own-UID primary-loopback experiment. No product PCM publication. */
final class StereoProbe {
    private static final Class<?>[] NONE=new Class<?>[0];
    private static int integer(Object o,String name)throws Exception{return (Integer)invoke(o,name,NONE);}
    private static JSONObject player(AudioManager manager,AudioTrack monitor,int source)throws Exception {
        JSONObject match=null;
        for(AudioPlaybackConfiguration c:manager.getActivePlaybackConfigurations()) {
            if(integer(c,"getSessionId")!=monitor.getAudioSessionId())continue;
            require(match==null,"ambiguous_monitor_session");
            int uid=integer(c,"getClientUid"),pid=integer(c,"getClientPid"),id=integer(c,"getPlayerInterfaceId");
            require(uid>=0 && uid!=source && pid==Process.myPid() && id>=0 && integer(c,"getPlayerType")==1 &&
                integer(c,"getPlayerState")==2 && c.getAudioAttributes().getUsage()==AudioAttributes.USAGE_MEDIA,"monitor_actual_identity_not_proven");
            match=new JSONObject().put("uid",uid).put("pid",pid).put("session",monitor.getAudioSessionId()).put("player",id);
        }
        return match;
    }
    private static JSONObject route(AudioTrack track)throws Exception {
        AudioDeviceInfo d=track.getRoutedDevice();
        require(d!=null && d.isSink() && d.getType()!=AudioDeviceInfo.TYPE_REMOTE_SUBMIX && d.getType()!=AudioDeviceInfo.TYPE_UNKNOWN,"monitor_physical_route_not_proven");
        return new JSONObject().put("id",d.getId()).put("type",d.getType()).put("address",d.getAddress());
    }
    private static int threshold(AudioTrack track) {
        return android.os.Build.VERSION.SDK_INT>=31?track.getStartThresholdInFrames():track.getBufferSizeInFrames();
    }
    private static JSONObject monitorBuffers(AudioTrack track)throws Exception {
        return new JSONObject().put("capacity_frames",track.getBufferCapacityInFrames())
            .put("effective_frames",track.getBufferSizeInFrames()).put("start_threshold_frames",threshold(track))
            .put("threshold_api",android.os.Build.VERSION.SDK_INT>=31).put("performance_mode",track.getPerformanceMode());
    }
    private static void configureMonitor(AudioTrack track,JSONObject e,String key)throws Exception {
        JSONObject receipt=new JSONObject().put("before",monitorBuffers(track));e.put(key,receipt);
        int sizeResult=track.setBufferSizeInFrames(StereoPending.BUFFER_FRAMES);
        receipt.put("size_result",sizeResult);
        int thresholdResult=android.os.Build.VERSION.SDK_INT>=31?
            track.setStartThresholdInFrames(StereoPending.START_FRAMES):track.getBufferSizeInFrames();
        receipt.put("threshold_result",thresholdResult);
        JSONObject actual=monitorBuffers(track);receipt.put("after",actual);
        StereoPending.monitorBounds(actual.getInt("capacity_frames"),actual.getInt("effective_frames"),
            actual.getInt("start_threshold_frames"),sizeResult,thresholdResult);
        e.put("monitor_actual_buffer_frames",actual.getInt("effective_frames"));
    }
    private static void stable(AudioManager manager,AudioTrack track,int uid,JSONObject who,JSONObject where)throws Exception {
        require(manager.getMode()==AudioManager.MODE_NORMAL,"communication_mode_changed");
        JSONObject current=player(manager,track,uid);
        require(current!=null && current.toString().equals(who.toString()),"monitor_identity_changed");
        require(route(track).toString().equals(where.toString()),"monitor_route_changed");
        int size=track.getBufferSizeInFrames(),start=threshold(track);
        StereoPending.monitorBounds(track.getBufferCapacityInFrames(),size,start,size,start);
    }
    private static JSONObject timestamp(AudioRecord record,AudioTrack monitor,StereoPending pending)throws Exception {
        AudioTimestamp r=new AudioTimestamp(),m=new AudioTimestamp();
        int status=record.getTimestamp(r,AudioTimestamp.TIMEBASE_MONOTONIC);
        boolean valid=monitor.getTimestamp(m);
        return new JSONObject().put("observed_ns",System.nanoTime()).put("record_status",status)
            .put("record_frame",status==AudioRecord.SUCCESS?r.framePosition:-1).put("record_ns",status==AudioRecord.SUCCESS?r.nanoTime:-1)
            .put("monitor_valid",valid).put("monitor_frame",valid?m.framePosition:-1).put("monitor_ns",valid?m.nanoTime:-1)
            .put("read_frames",pending.readFrames).put("written_frames",pending.writtenFrames)
            .put("monitor_head",Integer.toUnsignedLong(monitor.getPlaybackHeadPosition()));
    }
    static int run(int uid,byte[] identity,JSONObject e) {
        AudioTrack monitor=null;AudioRecord record=null;AudioManager manager=null;Object policy=null;
        HandlerThread thread=null;boolean attempted=false,clean=true;String failure=null,cleanupError="",stage="monitor_preflight";
        StereoStats stats=new StereoStats();StereoPending pending=new StereoPending();long elapsed=0,setup=SystemClock.elapsedRealtime();
        JSONObject who=null,where=null;short[] block=new short[960];
        try {
            e.put("physical_audibility_proven",false).put("requested_rate",48000).put("requested_channels",2).put("requested_encoding",2)
                .put("route_flags",2).put("privileged_capture",false).put("monitor_gain",1.0).put("read_buffer_frames",480)
                .put("monitor_requested_buffer_frames",960).put("capture_limit_ms",5500).put("write_stall_limit_ms",100).put("queue_limit_frames",4800);
            require(e.getJSONObject("permissions").getInt("MODIFY_AUDIO_ROUTING")==0,"routing_permission_not_proven");
            manager=(AudioManager)make("android.media.AudioManager",NONE);
            require(manager.getMode()==AudioManager.MODE_NORMAL,"communication_mode_active");
            int session=manager.generateAudioSessionId();require(session>0,"monitor_session_unavailable");
            AudioFormat format=new AudioFormat.Builder().setEncoding(2).setSampleRate(48000).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build();
            monitor=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE).build())
                .setAudioFormat(format).setSessionId(session).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(3840).build();
            require(monitor.getState()==AudioTrack.STATE_INITIALIZED && monitor.getSampleRate()==48000 && monitor.getChannelCount()==2 && monitor.getAudioFormat()==2,"monitor_format_mismatch");
            require(monitor.setVolume(1f)==AudioTrack.SUCCESS,"monitor_gain_failed");
            e.put("monitor_actual_buffer_frames",monitor.getBufferSizeInFrames()).put("monitor_rate",monitor.getSampleRate())
                .put("monitor_channels",monitor.getChannelCount()).put("monitor_encoding",monitor.getAudioFormat());
            configureMonitor(monitor,e,"monitor_initial_buffers");
            monitor.play();
            long silence=0;
            while(who==null && SystemClock.elapsedRealtime()-setup<2000) {
                require(!control(false),"stopped_before_ready");
                int n=monitor.write(block,0,block.length,AudioTrack.WRITE_NON_BLOCKING);
                require(n>=0 && (n&1)==0,"silent_monitor_write_failed");silence+=n/2;
                who=player(manager,monitor,uid);
                if(who!=null && monitor.getRoutedDevice()!=null)where=route(monitor);
                if(where==null)who=null;
                SystemClock.sleep(5);
            }
            require(who!=null && where!=null,"monitor_actual_identity_missing");
            e.put("monitor_identity",who).put("monitor_route",where).put("startup_silence_frames",silence);
            // Reset playback-head/frame origin without releasing the proven player identity.
            monitor.pause();monitor.flush();require(monitor.getPlaybackHeadPosition()==0,"startup_silence_not_cleared");
            configureMonitor(monitor,e,"monitor_routed_buffers");monitor.play();
            stage="register_own_uid_loopback";
            Class<?> ruleClass=Class.forName("android.media.audiopolicy.AudioMixingRule"),mixClass=Class.forName("android.media.audiopolicy.AudioMix"),policyClass=Class.forName("android.media.audiopolicy.AudioPolicy");
            Object rule=make("android.media.audiopolicy.AudioMixingRule$Builder",NONE);
            for(int usage:new int[]{AudioAttributes.USAGE_MEDIA,AudioAttributes.USAGE_GAME})invoke(rule,"addRule",new Class<?>[]{AudioAttributes.class,int.class},new AudioAttributes.Builder().setUsage(usage).build(),1);
            invoke(rule,"addMixRule",new Class<?>[]{int.class,Object.class},4,Integer.valueOf(uid));
            invoke(rule,"allowPrivilegedPlaybackCapture",new Class<?>[]{boolean.class},false);rule=invoke(rule,"build",NONE);
            Object mix=make("android.media.audiopolicy.AudioMix$Builder",new Class<?>[]{ruleClass},rule);
            invoke(mix,"setFormat",new Class<?>[]{AudioFormat.class},format);invoke(mix,"setRouteFlags",new Class<?>[]{int.class},2);mix=invoke(mix,"build",NONE);
            thread=new HandlerThread("phosphor-stereo-policy");thread.start();
            policy=make("android.media.audiopolicy.AudioPolicy$Builder",new Class<?>[]{Context.class},(Object)null);
            invoke(policy,"setLooper",new Class<?>[]{Looper.class},thread.getLooper());invoke(policy,"addMix",new Class<?>[]{mixClass},mix);policy=invoke(policy,"build",NONE);
            stable(manager,monitor,uid,who,where);
            attempted=true;int registration=(Integer)invoke(manager,"registerAudioPolicy",new Class<?>[]{policyClass},policy);
            e.put("registration",registration);require(registration==0,"policy_registration_rejected");
            stage="tagged_stereo_sink";record=(AudioRecord)invoke(policy,"createAudioRecordSink",new Class<?>[]{mixClass},mix);
            require(record!=null && record.getState()==AudioRecord.STATE_INITIALIZED,"stereo_sink_uninitialized");
            e.put("sample_rate",record.getSampleRate()).put("channels",record.getChannelCount()).put("encoding",record.getAudioFormat())
                .put("record_actual_buffer_frames",record.getBufferSizeInFrames()).put("record_requested_buffer_frames","framework_createAudioRecordSink");
            require(record.getSampleRate()==48000 && record.getChannelCount()==2 && record.getAudioFormat()==2,"actual_stereo_format_mismatch");
            stable(manager,monitor,uid,who,where);
            require(SystemClock.elapsedRealtime()-setup<10000,"setup_deadline");
            stage="ready";e.put("stage",stage);send(11,e);
            while(!control(true))require(SystemClock.elapsedRealtime()-setup<10000,"start_deadline");
            require(!stopped,"stopped_before_start");
            // The record starts after START so no pre-START silence enters the instrument window.
            record.startRecording();require(record.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING,"stereo_record_not_started");
            stage="capture";long start=SystemClock.elapsedRealtime(),lastWrite=start,lastRead=start,nextCheck=start,nextProgress=start,progress=0;
            long epoch=System.nanoTime();e.put("capture_epoch_ns",epoch);
            int underruns=monitor.getUnderrunCount();e.put("monitor_underruns_start",underruns);
            while((elapsed=SystemClock.elapsedRealtime()-start)<5500) {
                control(false);if(stopped)break;
                long now=SystemClock.elapsedRealtime();
                if(now>=nextCheck){stable(manager,monitor,uid,who,where);nextCheck=now+100;}
                if(!pending.pending()) {
                    int count=record.read(block,0,Math.min(block.length,(StereoStats.LIMIT-stats.frames)*2),AudioRecord.READ_NON_BLOCKING);
                    require(count>=0,"stereo_read_error_"+count);pending.read(count);stats.add(block,count);
                    if(count>0){lastRead=now;lastWrite=now;}
                    if(now>=nextProgress){sendBytes(16,Protocol.progress(identity,progress++));nextProgress=now+250;
                        JSONObject ts=timestamp(record,monitor,pending);
                        if(ts.getInt("record_status")==0) {
                            long backlog=ts.getLong("record_frame")-pending.readFrames;
                            require(backlog<=record.getBufferSizeInFrames(),"capture_buffer_overrun");
                            e.put("record_backlog_highwater_frames",Math.max(e.optLong("record_backlog_highwater_frames"),backlog));
                        }
                        if(!e.has("timestamp_first") && ts.getInt("record_status")==0 && ts.getBoolean("monitor_valid") &&
                            ts.getLong("record_ns")>=epoch && ts.getLong("monitor_ns")>=epoch)e.put("timestamp_first",ts);e.put("timestamp_last",ts);}
                }
                if(pending.pending()) {
                    int count=monitor.write(block,pending.offset,pending.count-pending.offset,AudioTrack.WRITE_NON_BLOCKING);
                    pending.wrote(count);if(count>0)lastWrite=now;
                    require(now-lastWrite<=100,"monitor_write_stall");
                }
                pending.queue(Integer.toUnsignedLong(monitor.getPlaybackHeadPosition()));
                require(now-lastRead<=100,"capture_read_stall");
                if(stats.frames==StereoStats.LIMIT && !pending.pending())break;
                SystemClock.sleep(1);
            }
            require(!stopped && !pending.pending(),"capture_stopped_or_pending_at_deadline");
            stable(manager,monitor,uid,who,where);
            e.put("timestamp_last",timestamp(record,monitor,pending)).put("monitor_head",Integer.toUnsignedLong(monitor.getPlaybackHeadPosition()))
                .put("monitor_underruns_end",monitor.getUnderrunCount());
            require(e.has("timestamp_first"),"timestamp_progress_unavailable");
            JSONObject first=e.getJSONObject("timestamp_first"),last=e.getJSONObject("timestamp_last");
            require(last.getInt("record_status")==0 && last.getBoolean("monitor_valid"),"terminal_timestamp_unavailable");
            double recordRate=StereoPending.rate(first.getLong("record_frame"),first.getLong("record_ns"),last.getLong("record_frame"),last.getLong("record_ns"));
            double monitorRate=StereoPending.rate(first.getLong("monitor_frame"),first.getLong("monitor_ns"),last.getLong("monitor_frame"),last.getLong("monitor_ns"));
            e.put("record_timestamp_rate",recordRate).put("monitor_timestamp_rate",monitorRate);
            require(recordRate>=47520 && recordRate<=48480 && monitorRate>=47520 && monitorRate<=48480,"timestamp_rate_mismatch");
            require(stats.stereo(),"independent_stereo_not_proven");
            require(pending.readFrames==pending.writtenFrames && e.getLong("monitor_head")>0,"monitor_progress_not_proven");
        }catch(Throwable error){failure=stage+": "+cause(error);}
        finally {
            // Queued monitor audio must stop before record stop can restore the original route.
            if(monitor!=null) {
                try{monitor.stop();monitor.flush();}catch(Throwable t){clean=false;cleanupError+=" monitor_stop:"+cause(t);}
                try{monitor.release();}catch(Throwable t){clean=false;cleanupError+=" monitor_release:"+cause(t);}
            }
            if(record!=null) {
                try{if(record.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)record.stop();}catch(Throwable t){clean=false;cleanupError+=" record_stop:"+cause(t);}
                try{record.release();}catch(Throwable t){clean=false;cleanupError+=" record_release:"+cause(t);}
            }
            if(attempted)try{invoke(manager,"unregisterAudioPolicy",new Class<?>[]{Class.forName("android.media.audiopolicy.AudioPolicy")},policy);}catch(Throwable t){clean=false;cleanupError+=" unregister:"+cause(t);}
            if(thread!=null)try{thread.quitSafely();thread.join(500);require(!thread.isAlive(),"policy_thread_alive");}catch(Throwable t){clean=false;cleanupError+=" thread:"+cause(t);}
            try {
                e.put("stage",stage).put("status",failure==null&&clean?"ok":"error").put("error",failure==null?"":failure)
                    .put("fix","Inspect identity, physical route and bounded cleanup. Do not change system policy.")
                    .put("cleanup_confirmed",clean).put("cleanup_error",cleanupError.substring(0,Math.min(500,cleanupError.length())))
                    .put("frames",stats.frames).put("measured_frames",stats.measured).put("reads",stats.reads).put("zero_reads",stats.zeroReads)
                    .put("written_frames",pending.writtenFrames).put("pending_samples",pending.count-pending.offset).put("queue_highwater_frames",pending.queueHighwater)
                    .put("queue_highwater_ms",pending.queueHighwater/48.0).put("software_queue_only",true).put("elapsed_ms",elapsed)
                    .put("rms_l",stats.rms(0)).put("rms_r",stats.rms(1)).put("peak",stats.peak).put("correlation",stats.correlation())
                    .put("separation_997_db",stats.separation(0)).put("separation_1499_db",stats.separation(1)).put("stereo_proven",stats.stereo());
                e.put("steady_start_frame",StereoStats.RATE).put("phase_sine_997_l",stats.phase(0,0)).put("phase_sine_1499_r",stats.phase(1,1));
                for(int c=0;c<2;c++)for(int f=0;f<2;f++)e.put("power_"+c+"_"+(f==0?997:1499),stats.power(c,f));
                StereoTerminal.Result terminal=StereoTerminal.prepare(identity,e.toString(),clean,Process.myUid());
                sendBytes(12,terminal.payload);
                if(terminal.compacted && failure==null)failure="terminal_receipt_overflow";
            }catch(Throwable t){clean=false;}
        }
        return failure==null&&clean?0:4;
    }
    private StereoProbe(){}
}
