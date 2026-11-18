# FaunaGBIF ProGuard / R8 Keep Rules

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Moshi & Domain Models
-keepclassmembers class com.example.model.** { *; }
-keepclassmembers class com.example.data.gbif.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# Retrofit
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_snuff.IgnoreJRERequirement
