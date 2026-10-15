# Retrofit
-dontwarn retrofit2.**
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking interface com.drakeerv.squeen.data.api.SpeedQueenApi

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.drakeerv.squeen.**$$serializer { *; }
-keepclassmembers class com.drakeerv.squeen.** {
    *** Companion;
}
-keepclasseswithmembers class com.drakeerv.squeen.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Koin
-keepclassmembers class * { @org.koin.core.annotation.* <fields>; }
-dontwarn org.koin.**

# ZXing
-dontwarn com.google.zxing.**
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }

# Compose
-dontwarn androidx.compose.**

# Aggressive dex shrink: merge the remaining classes into one package and allow
# R8 to relax access modifiers (safe for an application module).
-repackageclasses ''
-allowaccessmodification
