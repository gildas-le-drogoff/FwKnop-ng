# OpenKeychain AIDL stubs and parcelables are looked up by name across processes.
-keep class org.openintents.openpgp.** { *; }

# dnsjava references optional JNA / JNDI / sun.* backends that do not exist on Android.
-dontwarn com.sun.jna.**
-dontwarn javax.naming.**
-dontwarn sun.net.spi.nameservice.**
# JDK 18+ resolver SPI (META-INF/services, multi-release class): absent from Android, never loaded.
-dontwarn java.net.spi.InetAddressResolverProvider
-dontwarn org.xbill.DNS.spi.DnsjavaInetAddressResolverProvider
-dontwarn lombok.**
-dontwarn org.slf4j.**

# Strips debug and verbose logs (mostly from libraries) from release builds.
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}
