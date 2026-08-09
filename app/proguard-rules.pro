# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }

# Keep model classes used for CSV/JSON backup
-keep class com.kamal.smsfinance.data.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
