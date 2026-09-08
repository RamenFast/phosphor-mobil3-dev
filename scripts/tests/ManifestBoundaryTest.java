import java.nio.file.Files;
import java.nio.file.Path;

class ManifestBoundaryTest {
    static Path fixture;
    static int checks;
    static final String NS = "http://schemas.android.com/apk/res/android";
    static final String APP = "dev.phosphor.mobil3";

    static String manifest(String permissions, String attributes, String components) {
        return "<manifest xmlns:a='" + NS + "' package='" + APP + "'>" + permissions
            + "<application " + attributes + ">" + components + "</application></manifest>";
    }
    static String permission(String name) {
        return "<uses-permission a:name='android.permission." + name + "'/>";
    }
    static String service(String name, String type, boolean exported, String children) {
        return "<service a:name='." + name + "' a:exported='" + exported
            + "' a:foregroundServiceType='" + type + "'>" + children + "</service>";
    }
    static String purpose() {
        return "<property a:name='android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' a:value='User-started local visualization'/>";
    }
    static void test(String name, String xml, String expectedError) throws Exception {
        Files.writeString(fixture, xml);
        Exception failure = null;
        try { ManifestBoundary.check(fixture, true); } catch (Exception error) { failure = error; }
        if (expectedError == null && failure != null) throw new AssertionError(name + ": " + failure, failure);
        if (expectedError != null && (failure == null || !failure.getMessage().contains(expectedError)))
            throw new AssertionError(name + ": expected " + expectedError + ", got " + failure);
        checks++;
    }

    public static void main(String[] args) throws Exception {
        fixture = Path.of(args[0]);
        ManifestBoundary.check(Path.of(args[1]), false);
        checks++;
        String fgs = permission("FOREGROUND_SERVICE");
        String special = fgs + permission("FOREGROUND_SERVICE_SPECIAL_USE");
        String root = service("RootCaptureService", "specialUse", false, purpose());
        String hud = service("FloatingHudService", "specialUse", false, purpose());
        String mic = service("MicCaptureService", "microphone", false, "");
        String micPermissions = fgs + permission("FOREGROUND_SERVICE_MICROPHONE") + permission("RECORD_AUDIO");

        test("minimal merged", manifest("", "", ""), null);
        test("alternate namespace prefix", manifest(permission("INTERNET"), "", ""), null);
        test("formatted permission", manifest(permission("RECORD_AUDIO").replace(" a:name", "\n a:name"), "", ""), null);
        test("comment is not permission", manifest("<!-- <uses-permission a:name='android.permission.CAPTURE_AUDIO_OUTPUT'/> -->", "", ""), null);
        test("root candidate", manifest(special, "", root), null);
        test("HUD candidate", manifest(special + permission("SYSTEM_ALERT_WINDOW"), "", hud), null);
        test("mic candidate", manifest(micPermissions, "", mic), null);
        test("combined candidates", manifest(micPermissions + permission("FOREGROUND_SERVICE_SPECIAL_USE")
            + permission("SYSTEM_ALERT_WINDOW") + permission("BLUETOOTH_CONNECT") + permission("POST_NOTIFICATIONS"), "", root + hud + mic), null);
        test("legacy Bluetooth bounded", manifest(micPermissions + "<uses-permission a:name='android.permission.BLUETOOTH' a:maxSdkVersion='30'/>", "", mic), null);
        test("private root export", manifest(special, "", root.replace("a:exported='false'", "a:exported='true'")), "export state");
        test("root export must be explicit", manifest(special, "", root.replace("a:exported='false'", "")), "export state");
        test("root is not projection", manifest(special, "", root.replace("specialUse'", "mediaProjection'")), "foreground role");
        test("root purpose missing", manifest(special, "", service("RootCaptureService", "specialUse", false, "")), "declared purpose");
        test("root purpose blank", manifest(special, "", root.replace("User-started local visualization", " ")), "service purpose");
        test("root duplicate purpose", manifest(special, "", service("RootCaptureService", "specialUse", false, purpose() + purpose())), "service purpose");
        test("root foreground grant absent", manifest(fgs, "", root), "Missing foreground permission");
        test("root base FGS grant absent", manifest(permission("FOREGROUND_SERVICE_SPECIAL_USE"), "", root), "Missing foreground permission");
        test("orphan specialUse", manifest(special, "", ""), "no approved owner");
        test("orphan overlay", manifest(permission("SYSTEM_ALERT_WINDOW"), "", ""), "private HUD owner");
        test("HUD overlay missing", manifest(special, "", hud), "requires overlay permission");
        test("private HUD export", manifest(special + permission("SYSTEM_ALERT_WINDOW"), "", hud.replace("a:exported='false'", "a:exported='true'")), "export state");
        test("mic grant absent", manifest(fgs + permission("FOREGROUND_SERVICE_MICROPHONE"), "", mic), "requires RECORD_AUDIO");
        test("orphan microphone FGS", manifest(permission("FOREGROUND_SERVICE_MICROPHONE"), "", ""), "private mic service");
        test("orphan Bluetooth", manifest(permission("BLUETOOTH_CONNECT"), "", ""), "private mic service");
        test("legacy Bluetooth unbounded", manifest(micPermissions + permission("BLUETOOTH"), "", mic), "end at API 30");
        test("legacy Bluetooth extended", manifest(micPermissions + "<uses-permission a:name='android.permission.BLUETOOTH' a:maxSdkVersion='31'/>", "", mic), "end at API 30");

        for (String name : new String[] { "CAPTURE_AUDIO_OUTPUT", "WRITE_SECURE_SETTINGS", "QUERY_ALL_PACKAGES", "READ_SMS", "BLUETOOTH_SCAN", "UNKNOWN&#x9;NAME" })
            test("unapproved " + name, manifest(permission(name), "", ""), "Unapproved permission");
        test("sdk23 unapproved permission", manifest("<uses-permission-sdk-23 a:name='android.permission.CAPTURE_AUDIO_OUTPUT'/>", "", ""), "Unapproved permission");
        test("duplicate permission", manifest(permission("INTERNET") + permission("INTERNET"), "", ""), "Duplicate permission");
        test("permission expiry", manifest("<uses-permission a:name='android.permission.RECORD_AUDIO' a:maxSdkVersion='29'/>", "", ""), "cannot silently expire");
        test("spoofed attribute namespace", manifest(permission("INTERNET"), "", "").replace(NS, "urn:not-android"), "Unapproved permission");
        test("wrong root element", "<application/>", "Expected manifest root");
        test("namespaced root", manifest("", "", "").replace("<manifest ", "<manifest xmlns='urn:fake' "), "Expected manifest root");
        test("malformed XML", "<manifest><application>", "must start and end");
        test("missing application", "<manifest package='" + APP + "'/>", "one application");
        test("duplicate application", manifest("", "", "").replace("</manifest>", "<application/></manifest>"), "one application");
        test("debug package", manifest("", "", "").replace("package='" + APP + "'", "package='" + APP + ".debug'"), "Production package");
        test("shared UID", manifest("", "", "").replace("package=", "a:sharedUserId='android.uid.system' package="), "Shared UID");
        for (String flag : new String[] { "debuggable", "testOnly", "persistent" }) {
            test(flag + " enabled", manifest("", "a:" + flag + "='true'", ""), "android:" + flag);
            test(flag + " unresolved", manifest("", "a:" + flag + "='@bool/value'", ""), "android:" + flag);
            test(flag + " disabled", manifest("", "a:" + flag + "='false'", ""), null);
        }
        test("unknown Application", manifest("", "a:name='.UnexpectedApplication'", ""), "Application class");
        test("unknown component factory", manifest("", "a:appComponentFactory='com.example.Factory'", ""), "component factory");
        test("known component factory", manifest("", "a:appComponentFactory='androidx.core.app.CoreComponentFactory'", ""), null);
        test("unknown service", manifest("", "", "<service a:name='.Admin' a:exported='false'/>"), "Unapproved component");
        test("unknown provider", manifest("", "", "<provider a:name='.Admin' a:exported='false'/>"), "Unapproved component");
        test("component kind substitution", manifest(special, "", root.replace("service ", "receiver ").replace("</service>", "</receiver>")), "Unapproved component");
        test("activity alias", manifest("", "", "<activity-alias a:name='.MainActivity' a:exported='true'/>"), "Unapproved component");
        test("debug receiver", manifest("", "", "<receiver a:name='.SelfTestReceiver' a:exported='true'/>"), "Unapproved component");
        test("duplicate component", manifest(special, "", root + root), "Duplicate component");
        test("root added controller", manifest(special, "", root.replace("</service>", "<intent-filter><action a:name='dev.phosphor.CONTROL'/></intent-filter></service>")), "intent exposure");
        test("root empty filter", manifest(special, "", root.replace("</service>", "<intent-filter/></service>")), "intent exposure");
        test("root IPC permission", manifest(special, "", root.replace("a:name=", "a:permission='android.permission.DUMP' a:name=")), "IPC permission");
        test("instrumentation", manifest("", "", "").replace("<application", "<instrumentation a:name='.Runner'/><application"), "manifest element");
        test("wrong compatibility floor", manifest("<uses-sdk a:minSdkVersion='28' a:targetSdkVersion='36'/>", "", ""), "compatibility declaration");
        test("declared compatibility", manifest("<uses-sdk a:minSdkVersion='29' a:targetSdkVersion='36'/>", "", ""), null);
        test("required microphone", manifest("<uses-feature a:name='android.hardware.microphone' a:required='true'/>", "", ""), "hardware feature");
        test("optional microphone", manifest("<uses-feature a:name='android.hardware.microphone' a:required='false'/>", "", ""), null);
        test("unknown shared library", manifest("", "", "<uses-library a:name='com.example.Code' a:required='false'/>"), "shared library");
        test("known optional shared library", manifest("", "", "<uses-library a:name='androidx.window.extensions' a:required='false'/>"), null);

        String dynamic = "<permission a:name='" + APP + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' a:protectionLevel='signature'/>"
            + "<uses-permission a:name='" + APP + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'/>";
        test("AndroidX signature pair", manifest(dynamic, "", ""), null);
        test("AndroidX permission downgrade", manifest(dynamic.replace("protectionLevel='signature'", "protectionLevel='normal'"), "", ""), "permission declaration");
        test("missing dynamic declaration", manifest("<uses-permission a:name='" + APP + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'/>", "", ""), "declared and used together");
        String provider = "<provider a:name='androidx.startup.InitializationProvider' a:exported='false' a:authorities='" + APP
            + ".androidx-startup'><meta-data a:name='androidx.lifecycle.ProcessLifecycleInitializer' a:value='androidx.startup'/></provider>";
        test("AndroidX private startup", manifest("", "", provider), null);
        test("startup exported", manifest("", "", provider.replace("exported='false'", "exported='true'")), "export state");
        test("startup wrong authority", manifest("", "", provider.replace(APP + ".androidx-startup", APP + ".debug.androidx-startup")), "startup authority");
        test("startup extra initializer", manifest("", "", provider.replace("androidx.lifecycle.ProcessLifecycleInitializer", "com.example.AdminInitializer")), "initializer");
        test("startup URI grant", manifest("", "", provider.replace("<provider ", "<provider a:grantUriPermissions='true' ")), "grantUriPermissions");
        String profile = "<receiver a:name='androidx.profileinstaller.ProfileInstallReceiver' a:exported='true' a:permission='android.permission.DUMP'>";
        for (String action : new String[] { "INSTALL_PROFILE", "SKIP_FILE", "SAVE_PROFILE", "BENCHMARK_OPERATION" })
            profile += "<intent-filter><action a:name='androidx.profileinstaller.action." + action + "'/></intent-filter>";
        profile += "</receiver>";
        test("profile installer protected", manifest("", "", profile), null);
        test("profile installer unprotected", manifest("", "", profile.replace("a:permission='android.permission.DUMP'", "")), "IPC permission");
        test("profile extra action", manifest("", "", profile.replace("</receiver>", "<intent-filter><action a:name='dev.phosphor.CONTROL'/></intent-filter></receiver>")), "intent exposure");
        String activity = "<activity a:name='.MainActivity' a:exported='true'><intent-filter>"
            + "<action a:name='android.intent.action.MAIN'/><category a:name='android.intent.category.LAUNCHER'/>"
            + "</intent-filter></activity>";
        test("launcher routing", manifest("", "", activity), null);
        test("split launcher filter", manifest("", "", activity.replace("<category", "</intent-filter><intent-filter><category")), "intent exposure");
        test("launcher data routing", manifest("", "", activity.replace("</intent-filter>", "<data a:scheme='phosphor'/></intent-filter>")), "intent routing");
        test("launcher extra category", manifest("", "", activity.replace("</intent-filter>", "<category a:name='android.intent.category.BROWSABLE'/></intent-filter>")), "intent exposure");
        test("internal DTD", "<!DOCTYPE manifest [<!ENTITY name 'dev.phosphor.mobil3'>]>" + manifest("", "", ""), "DOCTYPE is disallowed");
        test("external DTD", "<!DOCTYPE manifest SYSTEM 'http://127.0.0.1:1/not-read'>" + manifest("", "", ""), "DOCTYPE is disallowed");
        test("external entity", "<!DOCTYPE manifest [<!ENTITY value SYSTEM 'file:///unreadable'>]>" + manifest("", "", ""), "DOCTYPE is disallowed");
        test("oversized XML", " ".repeat(2 * 1024 * 1024 + 1), "parser limit");
        System.out.println("Manifest policy checks passed: " + checks);
    }
}
