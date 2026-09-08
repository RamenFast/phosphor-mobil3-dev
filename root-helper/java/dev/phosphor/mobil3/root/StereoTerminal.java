package dev.phosphor.mobil3.root;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Full diagnostics or a fixed failure receipt. Cleanup truth never depends on text length. */
public final class StereoTerminal {
    public static final class Result {
        public final byte[] payload;
        public final boolean compacted;
        private Result(byte[] payload,boolean compacted){this.payload=payload;this.compacted=compacted;}
    }
    public static Result prepare(byte[] identity,String json,boolean clean,int actualUid)throws IOException {
        if(identity.length!=80 || actualUid!=0)throw new IOException("stereo_terminal_identity");
        String build=new String(identity,4,64,StandardCharsets.US_ASCII);
        int uid=Protocol.validateInit(new Protocol.Frame(10,identity),build),mode=Protocol.mode(identity);
        if(mode!=4 && mode!=5)throw new IOException("stereo_terminal_mode");
        int bytes=json.getBytes(StandardCharsets.UTF_8).length;
        if(bytes<=Protocol.MAX-80)return new Result(Protocol.tagged(identity,json),false);
        String compact="{\"protocol\":2,\"uid\":"+actualUid+",\"original_uid\":"+uid+
            ",\"build\":\""+build+"\",\"generation\":"+Protocol.generation(identity)+",\"mode\":"+mode+
            ",\"status\":\"error\",\"error\":\"terminal_receipt_overflow\",\"fix\":\"Full diagnostics exceeded the frame budget. Inspect host evidence before retrying.\""+
            ",\"cleanup_confirmed\":"+clean+",\"receipt_truncated\":true,\"full_receipt_bytes\":"+bytes+"}";
        return new Result(Protocol.tagged(identity,compact),true);
    }
    private StereoTerminal(){}
}
