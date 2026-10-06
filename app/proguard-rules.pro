# GeckoView is loaded partly through reflection and JNI.
-keep class org.mozilla.** { *; }
-dontwarn org.mozilla.**
# GeckoView reads its settings file with SnakeYAML via reflection.
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**
-dontwarn java.beans.**

# Raven's VPN: WireGuard's native library finds these by name.
-keep class com.wireguard.android.backend.GoBackend { native <methods>; }
-keep class com.wireguard.android.backend.GoBackend$VpnService { *; }
-keep class com.wireguard.android.util.SharedLibraryLoader { *; }
