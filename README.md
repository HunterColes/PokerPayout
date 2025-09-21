# **FatLine - FOSS Stock Portfolio App**

A free and open-source stock portfolio application built with modern Android development practices.

### **Purpose**
To provide users with a simple, free, and privacy-focused way to track their virtual stock investments without ads or data collection.

### **Description**
FatLine is a virtual stock portfolio application that:

- Connects to Yahoo Finance API in a respectful manner with proper rate limiting
- Allows users to search for stocks and add them to their virtual portfolio
- Tracks portfolio performance over time
- Stores all data locally for privacy (offline-first approach)
- Provides a clean, Material3 design interface
- Supports light/dark mode theming automatically
- Is completely free and open-source (GPL licensed)

### **Features**
- **🔍 Stock Search**: Search and discover stocks from major exchanges
- **📊 Portfolio Management**: Add stocks to your virtual portfolio and track performance
- **⚙️ Settings**: Customize app preferences and view about information


### **Libraries/concepts used**

* Gradle modularised project by features
* The Clean Architecture with MVI pattern in presentation layer
* Jetpack Compose with Material3 design - for UI layer
* Kotlin Coroutines & Kotlin Flow - for concurrency & reactive approach
* Kotlin Serialization converter - for JSON parsing
* Retrofit - for networking
* Hilt - for Dependency Injection pattern implementation
* Room - for local database
* Coil - for image loading
* Version Catalog - for dependency management
* Baseline and Startup Profiles - for performance improvements during app launch
* Timber - for logging
* JUnit5, Turbine and MockK - for unit tests
* Jetpack Compose test dependencies, Maestro and Hilt - for UI tests
* GitHub Actions - for CI/CD
* KtLint and Detekt - for code linting


Build Commands
Clean and Build:
```
gradlew clean build
```
Build Debug APK:
```
gradlew assembleDebug
```
Build Release APK:
```
gradlew assembleRelease
```
Install Commands
Install Debug APK to Connected Device:
```
gradlew installDebug
```
Install Release APK:
```
gradlew installRelease
```
Install and Run:
```
gradlew installDebug && adb shell am start -n com.huntercoles.fatline/.MainActivity
```
Debug Commands
Run Tests:
```
gradlew test
```