# Contributing

## Collaboration

This project uses the [Kull Convention](https://xerus2000.github.io/kull) to ensure consistent collaboration. It is recommended to read it in full, but here a short summary:

- Write commit messages in `type(scope): summary` format according to the [Karma Runner convention](http://karma-runner.github.io/latest/dev/git-commit-msg.html), using the [imperative mood](https://chris.beams.io/posts/git-commit/#imperative) for the summary and the "Closes" keyword in the footer for closed issues
- Create feature branches with descriptive names and utilize draft pull requests as documentation
- Only the reviewer resolves review comments to avoid confusion; do not add features to branches in review
- Pull requests should be merged by their creator after approval using squash or rebase, keeping a clean history

## Development 

The project is built using [Gradle](https://gradle.org/).

### Setup

1. Clone the project
2. Run the application using `./gradlew run` from the Terminal or by executing the Gradle run task from your IDE
3. Enable the shared git hooks by running `git config core.hooksPath .dev/githooks`
4. Start coding & submit a PR when you think you've made an improvement

To have Gradle use a specific JDK, create a `gradle.properties` file at the root of the project with the following line:
```
org.gradle.java.home=/path/to/jdk
```

In order to fetch the Catalog and Genres you have to create a file called `src/resources/sheets-api-key` and put an api key for Google Sheets into it.

### Important Gradle Tasks

| Name        | Action                                                                                   |
|-------------|------------------------------------------------------------------------------------------|
| `run`       | run the project right from source                                                        |
| `shadowJar` | create an executable jar in the root directory of the project bundled with all libraries |
| `build`     | build & test the whole project                                                           |

Provide the argument `-Dargs="--loglevel trace"` to the run task to change the log level or pass other commandline options to the application.

When running a self-compiled jar, the application might try to update itself to an earlier version due to missing information. To prevent this, add the `--no-update` flag.

### Logging

Logging is done via [slf4j](https://www.slf4j.org) wrapped by [kotlin-logging](https://github.com/MicroUtils/kotlin-logging) and carried out by [logback-classic](https://logback.qos.ch).

A Logger can be created anywhere via `val logger = KotlinLogging.logger { }` and will automatically pick up the context where it was instantiated. Prefer to create it statically to avoid creating a new logger object for each instance.  
Then use the `logger` object to log key information at the INFO level, debugging clues in DEBUG and more detailed/spammy information in TRACE. WARN should only be used at key points of the application or for non-critical but unusual exceptions. Use ERROR solely for critical exceptions.

The application runs in WARN by default, but the `run` task automatically passes arguments to run it at DEBUG. This can be changed using the `--loglevel` flag.  
The log is additionally saved to a file in `TEMP/monsterutilities/logs` at DEBUG level unless the log level is set to TRACE in which case it records everything.
