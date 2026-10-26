# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.kexu.campus.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.kexu.campus.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
