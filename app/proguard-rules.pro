# Web3j rules
-keep class org.web3j.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.web3j.**
-dontwarn org.bouncycastle.**
-dontwarn java.beans.**
-dontwarn com.fasterxml.jackson.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
    @kotlinx.serialization.SerialName <fields>;
}
-keep class com.voltic.app.chain.explorer.** { *; }

# ML Kit Barcode Scanning
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# WalletManager & Models
-keep class com.voltic.app.wallet.** { *; }
-keep class com.voltic.app.payload.** { *; }
-keep class com.voltic.app.ui.model.** { *; }
