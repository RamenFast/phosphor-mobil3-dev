import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

// Private parser for check-play-boundary.sh, not a product or public command surface.
class ManifestBoundary {
    static final String APP = "dev.phosphor.mobil3";
    static final String ANDROID = "http://schemas.android.com/apk/res/android";
    static final String PERMISSION = "android.permission.";
    static final String DYNAMIC = APP + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION";
    static final String SUBTYPE = "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE";
    static final Set<String> PERMISSIONS = Set.of(
        "INTERNET", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_MEDIA_PLAYBACK",
        "FOREGROUND_SERVICE_MEDIA_PROJECTION", "RECORD_AUDIO", "WAKE_LOCK",
        "SYSTEM_ALERT_WINDOW", "FOREGROUND_SERVICE_MICROPHONE",
        "FOREGROUND_SERVICE_SPECIAL_USE", "POST_NOTIFICATIONS", "BLUETOOTH_CONNECT", "BLUETOOTH"
    );
    static final Set<String> INITIALIZERS = Set.of(
        "androidx.emoji2.text.EmojiCompatInitializer",
        "androidx.lifecycle.ProcessLifecycleInitializer",
        "androidx.profileinstaller.ProfileInstallerInitializer"
    );
    record Component(String tag, boolean exported, String permission, String fgs,
                     Set<String> actions, Set<String> categories) {}
    static Component component(String tag, boolean exported, String permission, String fgs, String... actions) {
        return new Component(tag, exported, permission, fgs, Set.of(actions), Set.of());
    }
    static final Map<String, Component> COMPONENTS = Map.ofEntries(
        Map.entry(APP + ".MainActivity", new Component("activity", true, "", "",
            Set.of("android.intent.action.MAIN"), Set.of("android.intent.category.LAUNCHER"))),
        Map.entry(APP + ".PlaybackService", component("service", true, "", "mediaPlayback",
            "androidx.media3.session.MediaSessionService")),
        Map.entry(APP + ".CaptureService", component("service", false, "", "mediaProjection")),
        Map.entry(APP + ".CaptureNotificationListenerService", component("service", false,
            PERMISSION + "BIND_NOTIFICATION_LISTENER_SERVICE", "",
            "android.service.notification.NotificationListenerService")),
        Map.entry(APP + ".MicCaptureService", component("service", false, "", "microphone")),
        Map.entry(APP + ".RootCaptureService", component("service", false, "", "specialUse")),
        Map.entry(APP + ".FloatingHudService", component("service", false, "", "specialUse")),
        Map.entry("androidx.startup.InitializationProvider", component("provider", false, "", "")),
        Map.entry("androidx.profileinstaller.ProfileInstallReceiver", component("receiver", true,
            PERMISSION + "DUMP", "", "androidx.profileinstaller.action.INSTALL_PROFILE",
            "androidx.profileinstaller.action.SKIP_FILE", "androidx.profileinstaller.action.SAVE_PROFILE",
            "androidx.profileinstaller.action.BENCHMARK_OPERATION"))
    );

    static String attr(Element node, String key) { return node.getAttributeNS(ANDROID, key); }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    static List<Element> children(Element parent) {
        var result = new ArrayList<Element>();
        var nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element child) {
                require(child.getNamespaceURI() == null, "Namespaced manifest element is not allowed");
                result.add(child);
            }
        }
        return result;
    }
    static String className(String name) {
        if (name.startsWith(".")) return APP + name;
        return name.contains(".") ? name : APP + "." + name;
    }
    static void noFlag(Element node, String name) {
        require(!node.hasAttributeNS(ANDROID, name) || attr(node, name).equals("false"),
            "Forbidden or unresolved android:" + name);
    }

    static boolean exactEnum(String actual, String expected, boolean merged) {
        if (actual.equals(expected)) return true;
        if (!merged) return false;
        int value = switch (expected) {
            case "signature", "mediaPlayback" -> 2;
            case "mediaProjection" -> 32;
            case "microphone" -> 128;
            case "specialUse" -> 1073741824;
            default -> -1;
        };
        return value >= 0 && (actual.equals(Integer.toString(value))
            || actual.equals("0x" + Integer.toHexString(value))
            || actual.equals(String.format(java.util.Locale.ROOT, "0x%08x", value)));
    }

    static void check(Path path, boolean merged) throws Exception {
        require(Files.size(path) <= 2 * 1024 * 1024, "Manifest exceeds the two-MiB parser limit");
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            @Override public void error(SAXParseException error) throws SAXParseException { throw error; }
            @Override public void fatalError(SAXParseException error) throws SAXParseException { throw error; }
        });
        var root = builder.parse(path.toFile()).getDocumentElement();
        require(root.getTagName().equals("manifest") && root.getNamespaceURI() == null, "Expected manifest root");
        require((!merged && root.getAttribute("package").isEmpty()) || root.getAttribute("package").equals(APP),
            "Production package must be " + APP);
        require(attr(root, "sharedUserId").isEmpty(), "Shared UID is not permitted");
        var permissions = new HashSet<String>();
        var components = new HashMap<String, Element>();
        int applications = 0;
        int sdkDeclarations = 0;
        boolean dynamicDeclared = false;
        for (var node : children(root)) {
            String name = attr(node, "name");
            switch (node.getTagName()) {
                case "uses-permission", "uses-permission-sdk-23" -> {
                    boolean base = name.startsWith(PERMISSION) && PERMISSIONS.contains(name.substring(PERMISSION.length()));
                    boolean dependency = merged && (name.equals(PERMISSION + "ACCESS_NETWORK_STATE") || name.equals(DYNAMIC));
                    require(base || dependency, "Unapproved permission: " + name);
                    require(permissions.add(name), "Duplicate permission: " + name);
                    if (name.equals(PERMISSION + "BLUETOOTH"))
                        require(attr(node, "maxSdkVersion").equals("30"), "Legacy BLUETOOTH must end at API 30");
                    else require(attr(node, "maxSdkVersion").isEmpty(), "Permission cannot silently expire before a supported Android version: " + name);
                }
                case "permission" -> {
                    require(merged && name.equals(DYNAMIC) && !dynamicDeclared
                        && exactEnum(attr(node, "protectionLevel"), "signature", merged), "Unapproved permission declaration: " + name);
                    dynamicDeclared = true;
                }
                case "uses-sdk" -> {
                    require(++sdkDeclarations == 1, "Duplicate Android compatibility declaration");
                    require(attr(node, "minSdkVersion").equals("29")
                        && attr(node, "targetSdkVersion").equals("36") && attr(node, "maxSdkVersion").isEmpty(),
                        "Wrong Android compatibility declaration");
                }
                case "uses-feature" -> require(name.equals("android.hardware.microphone")
                    && attr(node, "required").equals("false"), "Unapproved or required hardware feature");
                case "application" -> {
                    require(++applications == 1, "Expected one application");
                    noFlag(node, "debuggable");
                    noFlag(node, "testOnly");
                    noFlag(node, "persistent");
                    require(attr(node, "permission").isEmpty(), "Application-wide IPC permission is not permitted");
                    require(attr(node, "name").isEmpty() || className(attr(node, "name")).equals(APP + ".PhosphorApplication"),
                        "Unapproved Application class");
                    require(attr(node, "appComponentFactory").isEmpty()
                        || (merged && attr(node, "appComponentFactory").equals("androidx.core.app.CoreComponentFactory")),
                        "Unapproved component factory");
                    for (var child : children(node)) {
                        if (child.getTagName().equals("uses-library")) {
                            require(merged && Set.of("androidx.window.extensions", "androidx.window.sidecar").contains(attr(child, "name"))
                                && attr(child, "required").equals("false"), "Unapproved shared library");
                            continue;
                        }
                        String childName = className(attr(child, "name"));
                        var policy = COMPONENTS.get(childName);
                        require(policy != null && policy.tag.equals(child.getTagName()), "Unapproved component: " + childName);
                        require(merged || childName.startsWith(APP + "."), "Dependency component belongs in merged output only");
                        require(components.putIfAbsent(childName, child) == null, "Duplicate component: " + childName);
                        checkComponent(child, childName, policy, merged);
                    }
                }
                default -> throw new IllegalArgumentException("Unapproved manifest element: " + node.getTagName());
            }
        }
        require(applications == 1, "Expected one application");
        require(!merged || sdkDeclarations == 1, "Merged manifest requires one Android compatibility declaration");
        require(permissions.contains(DYNAMIC) == dynamicDeclared, "Dynamic receiver signature permission must be declared and used together");
        for (var entry : components.entrySet()) {
            String type = COMPONENTS.get(entry.getKey()).fgs;
            if (type.isEmpty()) continue;
            String permission = switch (type) {
                case "mediaPlayback" -> "FOREGROUND_SERVICE_MEDIA_PLAYBACK";
                case "mediaProjection" -> "FOREGROUND_SERVICE_MEDIA_PROJECTION";
                case "microphone" -> "FOREGROUND_SERVICE_MICROPHONE";
                case "specialUse" -> "FOREGROUND_SERVICE_SPECIAL_USE";
                default -> throw new IllegalArgumentException("Unknown foreground role");
            };
            require(permissions.contains(PERMISSION + "FOREGROUND_SERVICE") && permissions.contains(PERMISSION + permission),
                "Missing foreground permission for " + entry.getKey());
        }
        if (permissions.contains(PERMISSION + "SYSTEM_ALERT_WINDOW"))
            require(components.containsKey(APP + ".FloatingHudService"), "Overlay permission requires the private HUD owner");
        if (components.containsKey(APP + ".FloatingHudService"))
            require(permissions.contains(PERMISSION + "SYSTEM_ALERT_WINDOW"), "HUD owner requires overlay permission");
        if (permissions.contains(PERMISSION + "FOREGROUND_SERVICE_MICROPHONE")
                || permissions.contains(PERMISSION + "BLUETOOTH_CONNECT") || permissions.contains(PERMISSION + "BLUETOOTH"))
            require(components.containsKey(APP + ".MicCaptureService"), "Microphone route permissions require the private mic service");
        if (components.containsKey(APP + ".MicCaptureService"))
            require(permissions.contains(PERMISSION + "RECORD_AUDIO"), "Mic service requires RECORD_AUDIO");
        if (permissions.contains(PERMISSION + "FOREGROUND_SERVICE_SPECIAL_USE"))
            require(components.containsKey(APP + ".RootCaptureService") || components.containsKey(APP + ".FloatingHudService"),
                "specialUse permission has no approved owner");
    }

    static void checkComponent(Element node, String name, Component policy, boolean merged) {
        require(attr(node, "exported").equals(Boolean.toString(policy.exported)), "Wrong explicit export state: " + name);
        require(attr(node, "permission").equals(policy.permission), "Wrong IPC permission: " + name);
        require(exactEnum(attr(node, "foregroundServiceType"), policy.fgs, merged), "Wrong foreground role: " + name);
        var actions = new HashSet<String>();
        var categories = new HashSet<String>();
        int subtypes = 0;
        for (var child : children(node)) {
            switch (child.getTagName()) {
                case "intent-filter" -> {
                    var filterActions = new HashSet<String>();
                    var filterCategories = new HashSet<String>();
                    for (var intent : children(child)) {
                        switch (intent.getTagName()) {
                            case "action" -> {
                                require(actions.add(attr(intent, "name")), "Duplicate action: " + name);
                                filterActions.add(attr(intent, "name"));
                            }
                            case "category" -> {
                                require(categories.add(attr(intent, "name")), "Duplicate category: " + name);
                                filterCategories.add(attr(intent, "name"));
                            }
                            default -> throw new IllegalArgumentException("Unapproved intent routing: " + name);
                        }
                    }
                    require(!filterActions.isEmpty() && policy.actions.containsAll(filterActions)
                        && filterCategories.equals(policy.categories), "Wrong intent exposure: " + name);
                }
                case "property" -> {
                    require(policy.fgs.equals("specialUse") && attr(child, "name").equals(SUBTYPE)
                        && !attr(child, "value").isBlank() && ++subtypes == 1, "Unapproved or missing service purpose: " + name);
                }
                case "meta-data" -> require(name.equals("androidx.startup.InitializationProvider")
                    && INITIALIZERS.contains(attr(child, "name")) && attr(child, "value").equals("androidx.startup"),
                    "Unapproved component initializer: " + name);
                default -> throw new IllegalArgumentException("Unapproved component child: " + name);
            }
        }
        require(actions.equals(policy.actions) && categories.equals(policy.categories), "Wrong intent exposure: " + name);
        require(!policy.fgs.equals("specialUse") || subtypes == 1, "specialUse needs a declared purpose: " + name);
        if (policy.tag.equals("provider")) {
            require(attr(node, "authorities").equals(APP + ".androidx-startup"), "Wrong startup authority");
            noFlag(node, "grantUriPermissions");
            require(attr(node, "readPermission").isEmpty() && attr(node, "writePermission").isEmpty(), "Unapproved provider permission");
        }
    }

    public static void main(String[] args) {
        try {
            require(args.length == 2 && Set.of("source", "merged").contains(args[0]), "Expected source|merged and manifest path");
            check(Path.of(args[1]), args[0].equals("merged"));
        } catch (Exception error) {
            System.err.println("Manifest boundary: " + error.getMessage());
            System.exit(4);
        }
    }
}
