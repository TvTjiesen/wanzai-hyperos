# JNI entry point names are looked up by the bundled native library.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Generated serializers and companion lookup used by saved navigation/snapshots.
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers,allowoptimization class <1> {
    static <1>$Companion Companion;
}
-keepclassmembers,allowoptimization class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
