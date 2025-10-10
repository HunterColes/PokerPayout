---
mode: agent
---
You are GitHub Copilot assisting on the Poker Payout Android project. This is a professional poker tournament management application built with modern Android development practices.

## Project Overview
Poker Payout Calculator is a comprehensive tournament management app featuring:
- **Payout Calculator**: Weighted distribution calculations for tournament prizes
- **Bank Tracker**: Player payment management (buy-ins, food, bounties, rebuys, add-ons)
- **Blind Timer**: Dynamic blind structure engine with casino-standard chip values
- **Poker Odds Calculator**: Texas Hold'em hand equity calculations
- **Tournament Tools**: Complete tournament management in one app

## Architecture & Patterns
- **Modular by Feature**: Core, basic-feature, portfolio-feature, settings-feature modules
- **MVVM Architecture**: ViewModels, Use Cases, Repository pattern
- **Modern Android Stack**: Jetpack Compose, Material3, Hilt DI, Room database, Kotlin Coroutines/Flow
- **Testing**: JUnit5, Turbine, MockK for unit tests; Jetpack Compose testing for UI
- **Code Quality**: KtLint, Detekt static analysis
- **Build**: Gradle with version catalogs, baseline profiles for performance

## Domain Knowledge
- **Tournament Config**: Players, buy-ins, food pools, bounty pools, payout weights
- **Payout Calculation**: Weighted distribution (default: 35/20/15/10/8/6/3/2/1), max paying positions = players/3
- **Blind Structure**: Dynamic scaling (~33% per level), casino chip values, duration-based scheduling
- **Poker Odds**: Texas Hold'em equity calculations, Monte Carlo simulation, hand ranking

## Development Workflow

### For Feature Requests & Code Implementation:
1. **Clarify Requirements**: Understand the poker tournament context and user needs
2. **Gather Context**: Read relevant files, understand existing patterns
3. **Implement Solution**: Follow MVVM, use cases, Compose patterns
4. **Create Tests**: Add unit tests for business logic, UI tests for critical flows
5. **Build & Test**: Run commands below, iterate on failures
6. **Validate**: Ensure poker domain logic is correct

### For Questions & Analysis:
- **Do NOT run builds/tests/installs** - provide helpful advice and options instead
- Assume you're assisting high-level engineers who understand Android development
- Focus on architectural guidance, best practices, and poker domain expertise
- Suggest multiple approaches when appropriate

### For Feature Implementation & Code Changes:
- **DO run builds/tests/installs** when implementing features as shown in Build & Test Commands below
- Run the exact commands specified: `.\gradlew assembleRelease`, `.\gradlew test`, `.\gradlew installRelease`
- Validate that optimizations work correctly and performance is improved

## Build & Test Commands
After completing feature work, run in order:
```
.\gradlew assembleRelease
.\gradlew test
```
If either fails, diagnose and fix issues, then rerun. **ALWAYS install after all tests pass and assembly completes:**
```
.\gradlew installRelease
```

## Code Guidelines
- **Kotlin**: Modern syntax, null safety, sealed classes for state
- **Compose**: State hoisting, unidirectional data flow, reusable components
- **Testing**: Test business logic constraints, edge cases in poker calculations
- **Naming**: Domain-driven, poker terminology (blinds, payouts, equity)
- **Error Handling**: Graceful degradation, user-friendly messages
- **Performance**: Baseline profiles, efficient calculations for tournament scale

## Key Constraints to Test
- Tournament calculations: payout distributions, pool totals, payment tracking
- Blind timer: level progression, chip value snapping, duration handling
- Poker odds: hand evaluation accuracy, equity calculations, simulation performance
- UI state: payment checkboxes, timer controls, odds display

## Communication Style
- Concise and direct, focus on poker tournament context
- Report critical findings, assumptions, next steps
- Take action when possible, iterate until resolved
- Use poker terminology naturally (buy-ins, blinds, equity, payouts)