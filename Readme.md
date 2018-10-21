# ![icon](assets/favicon.png) MonsterUtilities ![Discord](https://img.shields.io/discord/417314230681993226.svg?logo=discord) [![Build Status](https://semaphoreci.com/api/v1/xerus2000/monsterutilities/branches/master/shields_badge.svg)](https://semaphoreci.com/xerus2000/monsterutilities)

Browse, stream and download Monstercat Songs, powered by the Monstercat API and MCatalog

[How it all began](assets/Story.md)

## Usage

[Download](http://monsterutilities.bplaced.net/downloads?download) or use the GitHub releases.

> This is a pre-Release, you may encounter bugs. 
If you do, open an issue here or send feedback from inside the application. 
The latter will automatically include logs, which reside in `TEMP/monsterutilities/logs`

To run it, you need to have Java 8 by Oracle installed on your computer.

Read the initial guide and follow the tooltips. 
Improved user-friendliness is in development ;)

### Troubleshooting

#### connect.sid

For downloading and listening to the latest Track, your `connect.sid` 
needs to be entered in the bottom of the Downloader. It is a cookie that
identifies your Monstercat Account. Here's how to obtain it:

1) Log in on [monstercat.com](https://monstercat.com) and ensure that you have a valid Monstercat Gold subscription
2) Go to your browser cookies and search for `connect.monstercat.com`  
   [Quick link for Chrome](chrome://settings/cookies/detail?site=connect.monstercat.com)
3) Find the content of `connect.sid`. It is a string starting with `s%3A` and has around 90 characters.
4) Copy that string into the `connect.sid` Textfield at the bottom of the Downloader.

#### Downloader

Sometimes, the cache runs into issues and that may contribute to issues in the Downloader.
Simply disable the cache, restart the application and enable it again.

> If you still have issues - no problem! 
> Hit me up on [Discord](https://discord.gg/ZEusvHS) or send Feedback directly from the application!

## Screenshots

### Catalog

The Catalog provides an overview of all Tracks ever released on the label and 
extensive possibilities of filtering them.
> Tip: You can customize which columns to show by clicking on the `+` in the top right

![Catalog](assets/screenshots/catalog.png)
![Catalog filtering](assets/screenshots/filtering.png)

### Streaming

In case you missed it in the other Screenshots:  
There's a player on top that can stream any Monstercat track, just like the website. 
Double-click on any piece in the Catalog or Downloader to load it into the Player!
![Player](assets/screenshots/player.png)

### Downloader

You have a Monstercat Gold membership? Great, because now you can download whatever you want exactly how you want it!
![Downloader](assets/screenshots/downloader.png)
![Downloader](assets/screenshots/downloading.png)

### Customization

The application has multiple available skins and other options if the defaults don't suit your needs.
![Settings](assets/screenshots/settings.png)

## Development 

[Gradle](https://gradle.org/) is used for building the project.

### Setup

If you want Gradle to use a JDK other than your system default, 
create a `gradle.properties` file at the root of the project
with the following line: 
```
org.gradle.java.home=/path/to/jdk
```

To fetch the Catalog and Genres, you need to create the file `src/resources/sheets-api-key` 
and put an api key for Google Sheets into it.


### Important Tasks
 Name        | Action
 ---         | ---
 `run`       | runs the project right from source
 `shadowJar` | Creates an executable jar with all libraries bundled in the root directory of the project
 `runShadow` | Creates a shadowJar and runs it

Both run tasks can be run with the argument `-Dargs="--loglevel trace"`
to change the log level or pass other arguments to the application.

### Logging

Logging is done via slf4j wrapped by kotlin-logging and carried out by logback-classic.  
A Logger can be created anywhere via `val logger = KotlinLogging.logger { }`
and will automatically pick up the context where it was instantiated.

The application runs with the WARN log level by default, however both run tasks
automatically pass arguments to run it at DEBUG.

The application also logs to a file in `TMP/monsterutilities/logs`, 
the log level of which defaults to the lower of the console log level and DEBUG.