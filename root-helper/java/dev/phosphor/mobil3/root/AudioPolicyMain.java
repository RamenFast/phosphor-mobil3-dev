package dev.phosphor.mobil3.root;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.json.JSONObject;

public final class AudioPolicyMain {
    private static final int POLICY_SUCCESS = 0;
    private static final FileInputStream INPUT = new FileInputStream(FileDescriptor.in);
    private static byte[] identity;
    private static long generation;
    private static int mode;
    static boolean stopped;
    private static String stage = "init";
    private static JSONObject evidence;
    static Object invoke(Object target, String method, Class<?>[] types, Object... args) throws Exception {
        Class<?> owner = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        Method m = owner.getMethod(method, types);
        try { return m.invoke(target instanceof Class<?> ? null : target, args); }
        catch (InvocationTargetException e) { Throwable c = e.getCause(); if (c instanceof Exception) throw (Exception)c; throw e; }
    }
    static Object make(String name, Class<?>[] types, Object... args) throws Exception {
        return Class.forName(name).getConstructor(types).newInstance(args);
    }
    static void require(boolean value, String cause) { if (!value) throw new IllegalStateException(cause); }
    static void send(int kind, JSONObject data) throws Exception {
        sendBytes(kind, Protocol.tagged(identity, data.toString()));
    }
    static void sendBytes(int kind, byte[] payload) throws Exception {
        byte[] frame = Protocol.encode(kind, payload);
        // RuntimeInit redirects System.out. Write the inherited pipe explicitly.
        for (int n = 0; n < frame.length;) {
            int got = Os.write(FileDescriptor.out, frame, n, frame.length - n);
            if (got <= 0) throw new IllegalStateException("status_write_no_progress");
            n += got;
        }
    }
    private static boolean inputReady(int timeout) throws Exception {
        StructPollfd p = new StructPollfd(); p.fd = FileDescriptor.in;
        p.events = (short)(OsConstants.POLLIN | OsConstants.POLLHUP | OsConstants.POLLERR);
        return Os.poll(new StructPollfd[]{p}, timeout) > 0;
    }
    static boolean control(boolean waiting) throws Exception {
        if (!inputReady(waiting ? 20 : 0)) return false;
        Protocol.Frame f = Protocol.read(INPUT);
        Protocol.control(f, generation);
        if (f.kind == 3) { stopped = true; return true; }
        require(waiting && f.kind == 2, "helper_control_state");
        return true;
    }
    static String cause(Throwable e) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; e != null && i < 4; i++, e = e.getCause()) {
            if (i > 0) s.append(" <- ");
            s.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        }
        return s.substring(0, Math.min(700, s.length()));
    }
    private static JSONObject attribution() throws Exception {
        Object a = invoke(Class.forName("android.content.AttributionSource"), "myAttributionSource", new Class<?>[0]);
        return new JSONObject().put("uid", invoke(a,"getUid",new Class<?>[0]))
            .put("pid",invoke(a,"getPid",new Class<?>[0]))
            .put("package",invoke(a,"getPackageName",new Class<?>[0]));
    }
    private static JSONObject permissions() throws Exception {
        Object service = invoke(Class.forName("android.app.ActivityManager"),"getService",new Class<?>[0]);
        Method check = Class.forName("android.app.IActivityManager").getMethod("checkPermission",String.class,int.class,int.class);
        JSONObject data = new JSONObject();
        for (String name : new String[]{"MODIFY_AUDIO_ROUTING","CAPTURE_MEDIA_OUTPUT","CAPTURE_AUDIO_OUTPUT","RECORD_AUDIO"})
            data.put(name,check.invoke(service,"android.permission."+name,Process.myPid(),Process.myUid()));
        return data;
    }
    public static void main(String[] args) {
        AudioRecord record = null; Object policy = null, manager = null;
        HandlerThread thread = null; boolean registrationAttempted = false, cleanup = true;
        String failure = null, cleanupFailure = ""; ToneStats stats = new ToneStats(); long elapsed = 0;
        evidence = new JSONObject();
        try {
            require(args.length == 0 && Process.myUid() == 0, "fixed_helper_requires_actual_uid_zero");
            Os.fcntlInt(FileDescriptor.out, OsConstants.F_SETFL, Os.fcntlInt(FileDescriptor.out, OsConstants.F_GETFL,0) | OsConstants.O_NONBLOCK);
            Protocol.Frame init = Protocol.read(INPUT);
            int uid = Protocol.validateInit(init, HelperBuild.ID);
            identity = init.payload;
            generation = Protocol.generation(identity); mode = Protocol.mode(identity);
            require(mode == 0 || mode == 2 || mode == 3 || mode == 4 || mode == 5, "capture_mode_required");
            evidence.put("protocol",2).put("generation",generation).put("mode",mode).put("build",HelperBuild.ID).put("original_uid",uid)
                .put("uid",Process.myUid()).put("pid",Process.myPid()).put("sdk",Build.VERSION.SDK_INT);
            stage = "attribution"; if (Build.VERSION.SDK_INT >= 31) evidence.put("attribution",attribution());
            stage = "permissions"; evidence.put("permissions",permissions());
            if (mode >= 4) { System.exit(StereoProbe.run(uid, identity, evidence)); return; }
            stage = "reflection_rule";
            Class<?> ruleClass = Class.forName("android.media.audiopolicy.AudioMixingRule");
            Object rule = make("android.media.audiopolicy.AudioMixingRule$Builder",new Class<?>[0]);
            for (int usage : new int[]{AudioAttributes.USAGE_MEDIA,AudioAttributes.USAGE_GAME})
                invoke(rule,"addRule",new Class<?>[]{AudioAttributes.class,int.class},new AudioAttributes.Builder().setUsage(usage).build(),1);
            if (mode != 2) invoke(rule,"addMixRule",new Class<?>[]{int.class,Object.class},4,Integer.valueOf(uid));
            invoke(rule,"allowPrivilegedPlaybackCapture",new Class<?>[]{boolean.class},true);
            rule = invoke(rule,"build",new Class<?>[0]);
            stage = "reflection_mix";
            AudioFormat format = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            Class<?> mixClass = Class.forName("android.media.audiopolicy.AudioMix");
            Object mix = make("android.media.audiopolicy.AudioMix$Builder",new Class<?>[]{ruleClass},rule);
            invoke(mix,"setFormat",new Class<?>[]{AudioFormat.class},format);
            invoke(mix,"setRouteFlags",new Class<?>[]{int.class},3);
            mix = invoke(mix,"build",new Class<?>[0]);
            stage = "reflection_policy";
            thread = new HandlerThread("phosphor-root-policy"); thread.start();
            policy = make("android.media.audiopolicy.AudioPolicy$Builder",new Class<?>[]{Context.class},(Object)null);
            invoke(policy,"setLooper",new Class<?>[]{Looper.class},thread.getLooper());
            invoke(policy,"addMix",new Class<?>[]{mixClass},mix);
            policy = invoke(policy,"build",new Class<?>[0]);
            manager = make("android.media.AudioManager",new Class<?>[0]);
            stage = "register";
            Class<?> policyClass = Class.forName("android.media.audiopolicy.AudioPolicy");
            registrationAttempted = true;
            int registration = (Integer)invoke(manager,"registerAudioPolicy",new Class<?>[]{policyClass},policy);
            evidence.put("registration",registration); require(registration == POLICY_SUCCESS,"registration_rejected_"+registration);
            stage = "create_tagged_sink";
            record = (AudioRecord)invoke(policy,"createAudioRecordSink",new Class<?>[]{mixClass},mix);
            require(record != null,"sink_null");
            evidence.put("sample_rate",record.getSampleRate()).put("channels",record.getChannelCount())
                .put("encoding",record.getAudioFormat()).put("route_flags",3).put("state",record.getState());
            require(record.getState()==AudioRecord.STATE_INITIALIZED,"sink_uninitialized");
            require(record.getSampleRate()==16000 && record.getChannelCount()==1 && record.getAudioFormat()==AudioFormat.ENCODING_PCM_16BIT,"actual_format_mismatch");
            stage="start_recording";record.startRecording();require(record.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING,"not_recording");
            stage="ready";evidence.put("stage",stage);send(11,evidence);
            while (!control(true)) { /* supervisor owns the ready deadline */ }
            stage="read";long start=SystemClock.elapsedRealtime();short[] block=new short[160];
            long sequence=0, progress=0, nextProgress=0, streamFrames=0;
            while (!stopped && ((elapsed=SystemClock.elapsedRealtime()-start)<5000 || mode==2)) {
                control(false);
                if (stopped) break;
                if (mode==0 && stats.frames==ToneStats.LIMIT) { SystemClock.sleep(2); continue; }
                if (mode==3 && streamFrames>=80000) break;
                int count=record.read(block,0,mode==0 ? Math.min(block.length,ToneStats.LIMIT-stats.frames) : Protocol.readCount(mode,block.length,streamFrames),AudioRecord.READ_NON_BLOCKING);
                require(count>=0,"AudioRecord.read="+count);
                if (mode==0) stats.add(block,count);
                else {
                    if (count>0) { sendBytes(15,Protocol.pcm(identity,sequence++,block,count)); streamFrames+=count; }
                    if (elapsed>=nextProgress) { sendBytes(16,Protocol.progress(identity,progress++)); nextProgress=elapsed+250; }
                }
                SystemClock.sleep(2);
            }
            if (mode==0 && !stopped) require(stats.tonePresent(),"controlled_997Hz_tone_not_proven");
        } catch (Throwable error) { failure=stage+": "+cause(error); }
        finally {
            if (record != null) {
                try { if(record.getRecordingState()==AudioRecord.RECORDSTATE_RECORDING)record.stop(); }
                catch(Throwable error){cleanup=false;cleanupFailure+="stop: "+cause(error);}
                try { record.release(); }catch(Throwable error){cleanup=false;cleanupFailure+=" release: "+cause(error);}
            }
            if (registrationAttempted) try { invoke(manager,"unregisterAudioPolicy",new Class<?>[]{Class.forName("android.media.audiopolicy.AudioPolicy")},policy); }
                catch(Throwable error){cleanup=false;cleanupFailure+=" unregister: "+cause(error);}
            if (thread != null) try { thread.quitSafely();thread.join(500);if(thread.isAlive()){cleanup=false;cleanupFailure+=" policy_thread_alive";} }
                catch(Throwable error){cleanup=false;cleanupFailure+=" policy_thread: "+cause(error);}
            try {
                evidence.put("stage",stage).put("status",failure==null&&cleanup?"ok":"error")
                    .put("cleanup_confirmed",cleanup).put("cleanup_error",cleanupFailure.substring(0,Math.min(700,cleanupFailure.length())))
                    .put("error",failure==null?"":failure).put("fix","Inspect the named framework stage and controlled fixture. Do not alter system policy.")
                    .put("frames",stats.frames).put("nonzero",stats.nonzero).put("zero_reads",stats.zeroReads)
                    .put("rms",stats.rms()).put("peak",stats.peak).put("frequency_hz",stats.frequency())
                    .put("tone_ratio",stats.toneRatio()).put("elapsed_ms",elapsed);
                if(identity!=null)send(12,evidence);
            } catch(Throwable ignored) { cleanup=false; }
        }
        System.exit(failure==null&&cleanup?0:4);
    }
    private AudioPolicyMain() {}
}
