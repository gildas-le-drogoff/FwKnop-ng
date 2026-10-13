# OpenKeychain AIDL stubs and parcelables are looked up by name across processes.
-keep class org.openintents.openpgp.** { *; }

# dnsjava references optional JNA / JNDI / sun.* backends that do not exist on Android.
-dontwarn com.sun.jna.**
-dontwarn javax.naming.**
-dontwarn sun.net.spi.nameservice.**
-dontwarn lombok.**
-dontwarn org.slf4j.**
