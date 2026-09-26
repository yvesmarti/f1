# Keep the entry-point Activity reachable from the manifest declaration.
-keep public class com.f1sound.app.MainActivity { *; }

# ViewBinding generated classes — referenced reflectively by AndroidX.
-keep class **.databinding.* { *; }
