# **Poker Payout Calculator** 🃏

A sleek and comprehensive poker tournament management application built with modern Android development practices.

### **Purpose**
To provide poker tournament organizers and players with a complete toolkit for managing tournament payouts, tracking player payments, and timing blind levels with a beautiful poker-themed interface.

### **Description**
Poker Payout Calculator is a professional-grade tournament management application that:

- Calculates tournament payouts with customizable weight distributions
- Tracks player buy-ins, food pools, and bounty payments
- Provides real-time payment status and pool summaries
- Features a professional blind timer with countdown/countup modes
- Uses an authentic poker green color scheme
- Stores all data locally for privacy
- Provides a clean, intuitive interface optimized for tournament play
- Is completely free and open-source

### **Features**
- **🃏 Payout Calculator**: Configure tournament structure and calculate payouts with custom weights
- **🏦 Bank Tracker**: Track player payments with checkboxes for buy-in, food, and bounty
- **⏰ Blind Timer**: Professional tournament timer with visual progress indicators
	- Dynamic blind structure engine that scales blinds ~33% per level based on duration, round length, smallest chip, starting stack, and player count.
	- Skip forward/backward controls to preview upcoming levels or reset instantly from level one.
- **📊 Pool Management**: Real-time calculation of total pools and payment percentages
- **🎯 Tournament Tools**: Complete tournament management in one app

### **Libraries/concepts used**

* Gradle modularised project by features
* MVVM pattern with modern Android architecture
* Jetpack Compose with Material3 design - for UI layer
* Kotlin Coroutines & Kotlin Flow - for concurrency & reactive approach
* Hilt - for Dependency Injection pattern implementation
* Room - for local database
* Version Catalog - for dependency management
* Baseline and Startup Profiles - for performance improvements during app launch
* Timber - for logging
* JUnit5, Turbine and MockK - for unit tests
* Jetpack Compose test dependencies - for UI tests
* GitHub Actions - for CI/CD
* KtLint and Detekt - for code linting

### **Screenshots**
*Coming soon - tournament in progress!*

### **Blind Structure Engine**
The blind timer now generates a full tournament-ready blind schedule using your tournament inputs:

- Player count is read from the Tournament settings panel
- Duration, smallest chip, starting stack, and round length values drive the schedule
- Blinds increase between 33% and 100% each step and snap to casino-standard chip values
- The final big blind is always at least double the starting stack value to force decisive play
- Extra “sudden-death” levels are appended only when needed to finish near the target duration (duration + ~1 hour)
- The timer screen shows the active level, time until the next level, and a preview of upcoming levels
- Arrow controls let you jump between levels; tapping back on level one performs a full timer reset

### **Contributing**
We welcome contributions! Please feel free to submit a Pull Request.

### **License**
This project is licensed under the GPL License - see the [LICENSE](LICENSE.md) file for details.

---

## **Build Commands** 🔨
Clean and Build:
```
.\gradlew clean build
```
Build Debug APK:
```
.\gradlew assembleDebug
```
Build Release APK:
```
.\gradlew assembleRelease
```
Install Commands
Install Debug APK to Connected Device:
```
.\gradlew installDebug
```
Install Release APK:
```
.\gradlew installRelease
```
Install and Run:
```
.\gradlew installDebug && adb shell am start -n com.huntercoles.fatline/.MainActivity
```
Debug Commands
Run Tests:
```
.\gradlew test
```