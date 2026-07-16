# TUI visual testing

The TUI has ~50 automated test files; **those are the primary strategy for
verifying behavior**. Use the recipe below only to spot-check rendering that
can't be expressed as a unit test.

## `./gradlew :tui:run` does not work

`tui/build.gradle.kts` deliberately fails the `run` task:

```kotlin
// Gradle always runs tasks in a forked JVM that has no controlling terminal,
// so Mordant's enterRawMode() cannot work here. Run the JAR directly instead:
//   ./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar
tasks.named<JavaExec>("run") {
    doFirst {
        throw GradleException(
            "The TUI requires a direct terminal connection that Gradle cannot provide " +
            "(Gradle always forks a separate JVM detached from the terminal).\n" +
            "Build and run the JAR directly:\n" +
            "  ./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar"
        )
    }
}
```

Gradle always runs tasks in a forked JVM with no controlling terminal, so
Mordant's `enterRawMode()` cannot work. Build and run the jar directly:

```bash
./gradlew :tui:shadowJar && java -jar tui/build/libs/tui.jar
```

## tmux recipe

```bash
tmux new-session -d -s btech -x 220 -y 50
tmux send-keys -t btech 'java -jar tui/build/libs/tui.jar' Enter && sleep 3
tmux capture-pane -t btech -p                 # inspect output
tmux send-keys -t btech '<key>' ''            # send keystroke ('Tab','Enter','Escape','Up','c'…)
tmux kill-session -t btech
```

The jar must be built first (`./gradlew :tui:shadowJar`). Kill the session
when done.
