package dev.phosphor.mobil3.root;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.json.JSONObject;

public final class StereoTerminalTest {
    private static int checks;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    private interface Action { void run()throws Exception; }
    private static void rejects(Action action)throws Exception {
        try{action.run();throw new AssertionError("accepted");}catch(IOException expected){checks++;}
    }
    private static JSONObject parse(StereoTerminal.Result r,byte[] id)throws Exception {
        check(r.payload.length<=Protocol.MAX);
        check(Arrays.equals(id,Arrays.copyOf(r.payload,80)));
        Protocol.Frame frame=Protocol.read(new ByteArrayInputStream(Protocol.encode(12,r.payload)));
        check(frame.kind==12 && Arrays.equals(frame.payload,r.payload));
        JSONObject data=new JSONObject(new String(r.payload,80,r.payload.length-80,StandardCharsets.UTF_8));
        check(data.getInt("uid")==0 && data.getInt("original_uid")==10401 && data.getInt("protocol")==2);
        check(data.getString("build").equals("a".repeat(64)) && data.getLong("generation")==Long.MAX_VALUE);
        check(data.getInt("mode")==Protocol.mode(id));
        return data;
    }
    public static void main(String[] args)throws Exception {
        JSONObject observed=new JSONObject(Files.readString(Path.of(args[0]))).getJSONObject("data").getJSONObject("helper");
        for(int mode:new int[]{4,5}) {
            byte[] id=Protocol.identity(10401,"a".repeat(64),Long.MAX_VALUE,mode);
            for(boolean clean:new boolean[]{false,true})for(String text:new String[]{"", "\u0001".repeat(700), "🌸".repeat(700), "\\\"\n".repeat(700)}) {
                JSONObject full=new JSONObject(observed.toString()).put("protocol",2).put("uid",0).put("original_uid",10401)
                    .put("build","a".repeat(64)).put("generation",Long.MAX_VALUE).put("mode",mode).put("status","ok")
                    .put("cleanup_confirmed",clean).put("error",text).put("cleanup_error",text.isEmpty()?"":"\u0001".repeat(500));
                JSONObject buffers=new JSONObject().put("capacity_frames",8793).put("effective_frames",960)
                    .put("start_threshold_frames",480).put("threshold_api",true).put("performance_mode",0);
                JSONObject configured=new JSONObject().put("before",buffers).put("after",buffers).put("size_result",960).put("threshold_result",480);
                full.put("monitor_initial_buffers",configured).put("monitor_routed_buffers",configured);
                String serialized=full.toString();int length=serialized.getBytes(StandardCharsets.UTF_8).length;
                StereoTerminal.Result terminal=StereoTerminal.prepare(id,serialized,clean,0);
                JSONObject actual=parse(terminal,id);check(actual.getBoolean("cleanup_confirmed")==clean);
                check(terminal.compacted==(length>Protocol.MAX-80));
                if(terminal.compacted)check(actual.getString("status").equals("error") && actual.getBoolean("receipt_truncated") && actual.getInt("full_receipt_bytes")==length);
                else check(new String(terminal.payload,80,terminal.payload.length-80,StandardCharsets.UTF_8).equals(serialized));
            }
            String prefix="{\"padding\":\"",suffix="\"}";
            String exact=prefix+"x".repeat(Protocol.MAX-80-prefix.length()-suffix.length())+suffix;
            check(!StereoTerminal.prepare(id,exact,true,0).compacted);
            check(StereoTerminal.prepare(id,exact+" ",true,0).compacted);
            check(StereoTerminal.prepare(id,"🌸".repeat(1100),false,0).compacted);
            rejects(()->StereoTerminal.prepare(id,"{}",true,10401));
        }
        rejects(()->StereoTerminal.prepare(new byte[79],"{}",true,0));
        rejects(()->StereoTerminal.prepare(Protocol.identity(10401,"a".repeat(64),1,2),"{}",true,0));
        System.out.println("Stereo terminal JSON checks passed: "+checks);
    }
}
