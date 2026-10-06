# R8 rules for the release build (isMinifyEnabled + isShrinkResources in build.gradle.kts).
#
# No app-specific keep rules are needed today. Every reflective lookup the app makes is
# covered by a rule that its library already ships (AAR proguard.txt or
# META-INF/com.android.tools/r8/*.pro), plus proguard-android-optimize.txt:
#
#   Hilt / Dagger   hilt-android keeps the entry points; dagger's r8.pro has
#                   -identifiernamestring, so the @HiltViewModel LazyClassKey strings are
#                   rewritten to the obfuscated ViewModel names.
#   Compose         compose ui/runtime consumer rules.
#   Navigation      Type-safe routes are @Serializable data objects (NavigationDestination).
#                   kotlinx-serialization's R8 rules keep each route's INSTANCE and
#                   serializer(). The route string is the serialName constant that the
#                   compiler plugin bakes in, so obfuscated class names don't change it.
#   Room            room-runtime keeps `* extends RoomDatabase`, so AppDatabase_Impl can
#                   still be loaded by name.
#   @Parcelize      the default file keeps Parcelable CREATOR fields and enum
#                   values()/valueOf() (PlayerActionType is a parcelable enum).
#   Preferences     SharedPreferences hold plain strings and numbers ("COUNTDOWN",
#                   "player", ...). No enum or class is looked up by its name.
#
# Add a rule here only with a reproducer: a crash in the minified build (run
# `scripts/device/tour.sh --release`) or a documented library requirement.
# The rules R8 actually used: app/build/outputs/mapping/release/configuration.txt.

# Keep line numbers so a user's stack trace can be retraced with mapping.txt. The build
# is reproducible, so rebuilding the release tag regenerates the same mapping.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
