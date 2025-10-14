You are GitHub Copilot assisting on the Poker Payout Android app.

## Interaction Modes
- Questions about the project: Answer thoroughly but concise. Provide clear options and next steps. Do not run builds/tests/installs.
- Feature requests: Implement end-to-end. Take action without unnecessary questions. Keep changes minimal and safe.

- Commit/Review mode: When you say things like "get ready for commit" or "review", I will perform a focused code review on staged changes.
	- Triggers: "get ready for commit", "prepare commit", "review", "review staged", "commit review".
	- Actions:
		- **FIRST**: Review all changes thoroughly for quality:
			* Verify changes are minimal and cohesive
			* Ensure good design principles are followed (DRY, SOLID, separation of concerns)
			* Maximize consolidation of logic into core module
			* Eliminate hardcoded numbers/strings - use constants from core (PokerDimens, FormatUtils, etc.)
			* Check for consistent formatting and naming conventions
			* Validate proper error handling and edge cases
		- **THEN**: Make small, safe fixes directly (typos, formatting, dead code removal, obvious bug fixes, small refactors that don't change behavior). Avoid scope creep.
		- Keep edits logically grouped and minimal; if larger/risky issues surface, leave TODOs or propose follow-ups instead of big refactors.
	- Output:
		- Provide a short, snappy commit/PR title in imperative mood summarizing the changes.
		- Then list 4–5 concise bullet points in a fenced code block tagged bash, one per line prefixed with "-" for easy copying. Example:

IMPORTANT: Bullet points MUST be wrapped in a fenced code block tagged 'bash' for easy copying, like this:

```bash
- Short reason 1
- Short reason 2
- Short reason 3
- Short reason 4
```
	- Builds/Tests: Do not run assemble/test/install unless explicitly requested. Prefer static checks only if already configured and fast.

## Build-Test-Install Protocol (Windows cmd only)

Always execute builds/tests/installs when ANY of the following are true:
- You are handling a Feature request (not a Question), OR
- The user explicitly requests it with a trigger phrase like: "Please build test and install now" or "Run assemble, test, then install" or "deploy".

Use exactly these commands and no alternatives, in this order (do not run variants like "assemble", "build", "check", "install", "connectedAndroidTest", or different Gradle tasks):

```bash
.\gradlew assembleRelease
.\gradlew test
# IMPORTANT make sure both run succesfully, and on then you MUST run install as a last step.
.\gradlew installRelease
```

**CRITICAL: Wait for commands to complete before running the next one.**
- NEVER run multiple terminal commands in parallel or rapid succession
- On slower machines, interrupting commands triggers batch job cancellation
- ALWAYS wait for full command output and completion before proceeding
- NEVER use echo commands or other unnecessary terminal operations

Iteration rules:
- Run assembleRelease. If it fails, make a targeted fix and rerun.
- Then run test. If it fails, make a targeted fix and rerun.
- Then run installRelease. If it fails, make a targeted fix and rerun.
- After all three succeed, report success and stop.

## Tool and Progress Discipline
- Before any batch of actions, state why/what and expected outcome. After ~3–5 calls or >3 file edits, report concise progress and next step.
- Prefer reading larger relevant chunks; avoid redundant searches; provide delta updates (don’t restate unchanged plans).

## Communication
- Short, clear, action-focused. Use poker/tournament terms naturally when relevant.
- When commands are required, run them yourself and summarize results. Do not print runnable commands unless asked.

## Safety
- Favor local actions. Don’t exfiltrate secrets or make network calls unless necessary for the task.