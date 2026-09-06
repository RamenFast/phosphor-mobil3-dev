public final class PhoneGestureBoundsTest {
  static int rejected;
  static void rejects(float... points) {
    try {
      PhoneGesture.validate(points);
      throw new AssertionError("invalid coordinates accepted");
    } catch (IllegalArgumentException expected) {
      rejected++;
    }
  }
  public static void main(String[] args) {
    PhoneGesture.width=1080;
    PhoneGesture.height=2400;
    PhoneGesture.validate(new float[]{0,0,1079,2399});
    rejects(-1,0);
    rejects(1080,0);
    rejects(0,-1);
    rejects(0,2400);
    rejects(Float.NaN,1000);
    rejects(500,Float.POSITIVE_INFINITY);
    rejects(350,1000,Float.NEGATIVE_INFINITY,1000);
    if (rejected!=7) throw new AssertionError("missing cases");
    System.out.println("Bounds checks passed: valid ASUS corners and seven rejected inputs. No Android input injected.");
  }
}
