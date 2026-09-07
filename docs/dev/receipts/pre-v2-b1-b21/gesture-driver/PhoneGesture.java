import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import java.lang.reflect.Method;

/** Shell-only, finite physical-input acceptance driver. No app runtime endpoint. */
public final class PhoneGesture {
  static Object manager;
  static Method inject;
  static long down;
  static int width, height;
  static void validate(float[] xy) {
    for (int i=0; i<xy.length; i+=2) {
      if (!Float.isFinite(xy[i]) || !Float.isFinite(xy[i+1]) ||
          xy[i]<0 || xy[i]>=width || xy[i+1]<0 || xy[i+1]>=height)
        throw new IllegalArgumentException("point outside declared surface");
    }
  }
  static void emit(int action, float[] xy) throws Exception {
    validate(xy);
    int count=xy.length/2;
    MotionEvent.PointerProperties[] p=new MotionEvent.PointerProperties[count];
    MotionEvent.PointerCoords[] c=new MotionEvent.PointerCoords[count];
    for(int i=0;i<count;i++) {
      p[i]=new MotionEvent.PointerProperties(); p[i].id=i; p[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
      c[i]=new MotionEvent.PointerCoords(); c[i].x=xy[i*2]; c[i].y=xy[i*2+1]; c[i].pressure=1; c[i].size=1;
    }
    long now=SystemClock.uptimeMillis();
    MotionEvent e=MotionEvent.obtain(down,now,action,count,p,c,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
    boolean ok=(Boolean)inject.invoke(manager,e,2); e.recycle();
    System.out.println("{\"event\":\"input\",\"uptime_ms\":"+now+",\"action\":"+action+",\"pointers\":"+count+",\"accepted\":"+ok+"}");
    if(!ok) throw new IllegalStateException("input rejected");
  }
  static void tap(float x,float y) throws Exception {
    down=SystemClock.uptimeMillis(); emit(MotionEvent.ACTION_DOWN,new float[]{x,y});
    SystemClock.sleep(55); emit(MotionEvent.ACTION_UP,new float[]{x,y});
  }
  public static void main(String[] a) throws Exception {
    if (a.length<3) throw new IllegalArgumentException("width height command required");
    width=Integer.parseInt(a[0]); height=Integer.parseInt(a[1]);
    if (width<1 || height<1 || width>16384 || height>16384)
      throw new IllegalArgumentException("surface dimensions 1..16384");
    a=java.util.Arrays.copyOfRange(a,2,a.length);
    Class<?> cls=Class.forName("android.hardware.input.InputManagerGlobal");
    manager=cls.getMethod("getInstance").invoke(null);
    inject=cls.getMethod("injectInputEvent",android.view.InputEvent.class,int.class);
    if(a.length==1 && a[0].equals("probe")) {
      System.out.println("{\"event\":\"ready\",\"input_injected\":false,\"width\":"+width+",\"height\":"+height+"}");
    } else if(a.length==4 && a[0].equals("doubletap")) {
      float x=Float.parseFloat(a[1]), y=Float.parseFloat(a[2]); int gap=Integer.parseInt(a[3]);
      if(gap<40 || gap>500) throw new IllegalArgumentException("gap 40..500 ms");
      validate(new float[]{x,y});
      tap(x,y); SystemClock.sleep(gap); tap(x,y);
    } else if((a.length==10 && a[0].equals("pinch")) || (a.length==12 && a[0].equals("tap-pinch"))) {
      boolean preceded = a[0].equals("tap-pinch");
      int offset = preceded ? 3 : 1;
      float[] trigger = preceded ? new float[]{Float.parseFloat(a[1]),Float.parseFloat(a[2])} : null;
      float[] start=new float[4], end=new float[4];
      for(int i=0;i<4;i++){ start[i]=Float.parseFloat(a[offset+i]); end[i]=Float.parseFloat(a[offset+4+i]); }
      int duration=Integer.parseInt(a[offset+8]); if(duration<100 || duration>2000) throw new IllegalArgumentException("duration 100..2000 ms");
      validate(start); validate(end);
      if (trigger != null) { validate(trigger); tap(trigger[0],trigger[1]); }
      int steps = preceded ? 240 : 20;
      down=SystemClock.uptimeMillis();
      try {
        emit(MotionEvent.ACTION_DOWN,new float[]{start[0],start[1]});
        emit(MotionEvent.ACTION_POINTER_DOWN | (1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),start);
        for(int n=1;n<=steps;n++) {
          long target=down+(long)duration*n/steps; SystemClock.sleep(Math.max(0,target-SystemClock.uptimeMillis()));
          float[] xy=new float[4]; for(int i=0;i<4;i++) xy[i]=start[i]+(end[i]-start[i])*n/(float)steps;
          emit(MotionEvent.ACTION_MOVE,xy);
        }
        emit(MotionEvent.ACTION_POINTER_UP | (1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),end);
        emit(MotionEvent.ACTION_UP,new float[]{end[0],end[1]});
      } catch(Exception e) { emit(MotionEvent.ACTION_CANCEL,start); throw e; }
    } else throw new IllegalArgumentException("doubletap x y gap | pinch x1 y1 x2 y2 endX1 endY1 endX2 endY2 duration | tap-pinch tapX tapY followed by pinch arguments");
    System.out.println("{\"status\":\"ok\",\"tool\":\"phone-gesture-test\",\"version\":\"3\",\"ts\":\""+java.time.Instant.now()+"\"}");
  }
}
