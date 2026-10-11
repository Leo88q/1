# Ядро игры — чистая логика без рефлексии, отдельные правила не нужны.
# MWA использует kotlinx-serialization внутри: сохраняем сериализаторы на будущее (R8 включим после беты).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.solanamobile.mobilewalletadapter.** { *; }
