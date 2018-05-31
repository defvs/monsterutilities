Me, 05.08.2017: "What was that Feint track that was not DnB? How could I find out without digging through everything?" - 
"Hey I recently found this spreadsheet called the [MCatalog](https://rebrand.ly/mcatalog). 
But how do I conveniently query it for an Artist and an inverted Genre?"

An idea was born. I discovered the Google Spreadsheets API and created a first version 
(this isn't quite the original, but close enough):

![0.1.1](assets/screenshots/old/catalog.png)

Then I bought Monstercat Gold and wanted to download all tracks. So I searched for tools that did that.
But after some searching I came to the conclusion that there was no really helpful tool around.
I was neither satisfied with the default naming pattern when downloading from Monstercat, nor did I want to deal with a 
bazillion zips and cover.false-files. So I settled out to explore the possibilities of the Monstercat API.
After some work and experimentation, I created this:

![0.1.1](assets/screenshots/old/downloader.png)

But there were still some major points I wanted to address:

- Searching the Catalog was too inflexible, and there was no possibility to invert a query
- The Interface was straightup ugly
- The website can stream songs, so why wouldn't I?

So I decided to take on a rework, with a full UI overhaul. This is the current version. 
It's still not finished, but it does mostly work:

![Catalog](assets/screenshots/filtering_house.png)