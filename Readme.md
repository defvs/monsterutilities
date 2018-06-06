# ![icon](assets/favicon.png) MonsterUtilities

Browse, stream and download Monstercat Songs, powered by the Monstercat API and MCatalog

[How it all began](assets/Story.md)

Talk, ask questions and submit feedback on the Discord server:

![Discord](https://img.shields.io/discord/417314230681993226.svg?logo=discord)

## Usage

[Download](http://monsterutilities.bplaced.net/downloads?download)

> This is a pre-Release, you may encounter bugs. 
If you do, open an issue here or send feedback from inside the application. 
The latter will automatically include logs, which reside in `TEMP/monsterutilities/logs`

To run it, you need to have Java 8 by Oracle installed on your computer.

Read the initial guide and follow the tooltips. Improved user-friendliness is in development ;)

## Screenshots

### Catalog

The Catalog provides an overview of all Tracks ever released on the label and extensive possibilities of filtering them.
> Tip: You can customize which columns to show by clicking on the `+` in the top right

![Catalog](assets/screenshots/catalog.png)
![Catalog filtering](assets/screenshots/filtering.png)

### Streaming

In case you missed it in the other Screenshots: There's a player on top that can stream any Monstercat track, 
just like the website. Double-click on any piece in the Catalog or Downloader to load it into the Player!

![Player](assets/screenshots/player.png)

### Downloader

You have a Monstercat Gold membership? Great, because now you can download whatever you want exactly how you want it!
![Downloader](assets/screenshots/downloader.png)
![Downloader](assets/screenshots/downloading.png)

### Configurable

The application has multiple available skins and other options if the defaults don't suit your needs.
![Settings](assets/screenshots/settings.png)

## Development 

Gradle is used for building the project.

Before building, my [util project](https://github.com/Xerus2000/util) needs to be checked out in the parent directory.