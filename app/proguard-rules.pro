# R8 quita el código de librerías que la app no usa (p. ej. la colección completa de íconos),
# lo que achica mucho el APK. El código propio de HemoCheck se conserva entero para evitar
# problemas con Room, kotlinx.serialization y Compose.
-keep class com.hemocheck.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations

# SQLCipher usa JNI
-keep class net.zetetic.database.** { *; }

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class ** { kotlinx.serialization.KSerializer serializer(...); }

# Anotaciones de compilación que algunas librerías referencian pero no se incluyen
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.j2objc.annotations.**

# Sin registros en la versión de uso
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
