# Add project specific ProGuard rules here.
-keep class com.example.iqmemo.data.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
