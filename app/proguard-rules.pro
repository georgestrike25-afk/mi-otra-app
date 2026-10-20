# La versión de uso se compila sin minimizar (isMinifyEnabled = false).
# Si se habilita R8, conservar SQLCipher (usa JNI) y eliminar cualquier llamada a Log.
-keep class net.zetetic.database.** { *; }
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
