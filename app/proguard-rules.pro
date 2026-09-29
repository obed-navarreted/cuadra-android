# Reglas de release (R8 activo: la app se encoge y se ofusca). Retrofit, OkHttp y Room traen sus propias reglas de consumidor;
# aquí va lo de kotlinx.serialization, que se usa por reflexión ligera sobre los DTO.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.cuadra.caja.**$$serializer { *; }
-keepclassmembers class com.cuadra.caja.** { *** Companion; }
-keepclasseswithmembers class com.cuadra.caja.** { kotlinx.serialization.KSerializer serializer(...); }
# Los stack traces de producción siguen siendo legibles con el mapping.txt de cada versión.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
