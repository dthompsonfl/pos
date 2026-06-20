# Keep payment SDK classes
-keep class com.stripe.** { *; }
-keep class com.squareup.sdk.reader.** { *; }
-keep class com.shopify.** { *; }

# Keep our serializable models
-keep class com.enterprise.pos.core.** { *; }
-keep class com.enterprise.pos.domain.model.** { *; }
-keep class com.enterprise.pos.payment.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keepclassmembers class * { @javax.inject.Inject *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
