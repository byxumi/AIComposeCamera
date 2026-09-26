# 默认混淆规则（本工程未启用 minify，保留以备 release 使用）
-keepattributes Signature
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**