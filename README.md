# ![icon](assets/favicon.png) MonsterUtilities ![Discord](https://img.shields.io/discord/417314230681993226.svg?logo=discord) [![Build Status](https://semaphoreci.com/api/v1/xerus2000/monsterutilities/branches/master/shields_badge.svg)](https://semaphoreci.com/xerus2000/monsterutilities)

Browse, stream and download Monstercat Songs, powered by the Monstercat API and MCatalog. [This is the story of how it came to be.](assets/Story.md)

- [Getting started](#getting-started)
- [Troubleshooting](#troubleshooting)
- [Caching & Offline usage](#caching--offline-usage)
- [Screenshots](#screenshots)

_The application is still in a beta state, you may encounter bugs. Please report issues via GitHub and send a report from inside the application. The latter will automatically include logs from `TEMP/monsterutilities/logs`._

## Getting started

Download the portable version from [the website](http://monsterutilities.bplaced.net/downloads?download) or from [GitHub releases](https://github.com/Xerus2000/monsterutilities/releases).  
Download OS-specific installers from [GitHub releases](https://github.com/Xerus2000/monsterutilities/releases) with an optional bundled Java environment.

If you did not choose a download with a bundled JRE, the application requires [Java 8 by Oracle](https://www.java.com/de/download/manual.jsp) to be installed on your computer.

Make sure to read the initial in-app guide and watch out for tooltips.

## Troubleshooting

#### Authentication - the connect.sid

For downloading and listening to early access, your `connect.sid` needs to be entered in the bottom of the Downloader. It is a cookie that identifies your Monstercat Account. 
You can either login with your credentials or obtain it manually as described below.

1) Log in on [monstercat.com/gold](https://www.monstercat.com/gold) and ensure that you have a valid Monstercat Gold subscription
2) Go to your browser cookies and search for `connect.monstercat.com`  
   For Chromium-based browsers: `chrome://settings/cookies/detail?site=connect.monstercat.com`
3) Find the content of `connect.sid`. It is a string starting with `s%3A` and has around 90 characters.
4) Copy that string into the `connect.sid` Textfield at the bottom of the Downloader.

#### Downloader

Sometimes the cache runs into issues which may cause problems in the Downloader. In that case use the "Clear cache & Restart" button in the settings to reset the cache.

> If you still have issues, hit me up on [Discord](https://discord.gg/ZEusvHS) or send Feedback directly from the application!

## Caching & Offline usage

Upon starting the application for the first time, it will fetch and cache all Releases as well as Sheets (Catalog/Genres). This might take some time depending on your internet connection. On subsequent use it will prefer to fetch incrementally, reducing the load on your connection as well as Monstercat's Servers.  
Once fetched, Songs can be **browsed offline** but **not played or downloaded**.

The cache as well as logs are stored in the TEMP directory, depending on your operating system:
- Windows: `C:\Users\<username>\AppData\Local\Temp\monsterutilities` - can be changed by editing the `java.io.tmpdir` system property
- Unix: `/var/tmp/monsterutilities`, or under `/tmp` if `/var/tmp` does not exist

## Screenshots

### Catalog

The Catalog provides an overview of all Tracks ever released on the label with extensive filtering possibilities.

_Tip: You can customize which columns to show by clicking the `+` in the top right of the table._

![Catalog](assets/screenshots/catalog.png)
![Catalog filtering](assets/screenshots/filtering.png)

### Streaming

There is a player on the top that can stream any Monstercat track, just like the website. 
Double-click on any piece in the Catalog or Downloader to load it into the Player!

![Player](assets/screenshots/player.png)

### Downloader

If you have Monstercat Gold you can bulk download everything according to your preferences!

![Downloader](assets/screenshots/downloader.png)
![Downloader](assets/screenshots/downloading.png)

### Customization

The application has various color schemes and configuration options.

![Settings](assets/screenshots/settings.png)

## Acknowledgments

Thanks to [![install4j](https://www.ej-technologies.com/images/product_banners/install4j_small.png)](https://www.ej-technologies.com/products/install4j/overview.html) for providing us a free license to build the installers.
