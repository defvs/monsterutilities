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

### Configurable

The application has multiple available skins and other options if the defaults don't suit your needs.
![Settings](assets/screenshots/settings.png)

## Development 

Gradle is used for building the project.
