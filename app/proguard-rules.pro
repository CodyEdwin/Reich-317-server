# The server uses reflection free code, but the model classes are serialized
# with Gson, so their fields have to be kept.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.elvarg.game.definition.** { *; }
-keep class com.elvarg.game.model.** { *; }
-keep class com.elvarg.game.content.** { *; }
-keep class com.elvarg.net.login.** { *; }
-keep class com.elvarg.util.** { *; }
-dontwarn sun.misc.**
-dontwarn javax.naming.**
-dontwarn javax.tools.**
