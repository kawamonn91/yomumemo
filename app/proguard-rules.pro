# R8のバイトコード最適化(-optimize、shrink/obfuscateとは別の段階)が、CameraXの
# ProcessCameraProvider非同期コールバック + Compose(AndroidView)の組み合わせで
# NullPointerExceptionを生む不具合を実機再現で確認した(debugビルドでは再現しない = R8の
# 最適化段階に起因)。shrink/obfuscateによるサイズ削減・難読化の効果は保ったまま、
# 問題を起こしている最適化だけを無効化する。
-dontoptimize

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
