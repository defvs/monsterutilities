# Monstercat Connect API unofficial doc

All API calls go to https://connect.monstercat.com/

## Overview
| URL   |      HTTP Verb      |  Functionality |
|---|---|---|
| /signin                   | POST | [Signs you in](#signin) |
| /api/catalog/release      | GET | [Returns all releases (=albums)](#apicatalogrelease) |
| /api/catalog/track        | GET | [Returns all tracks](#apicatalogtrack) |
| /api/playlist             | GET | [Returns all playlists of the user](#apiplaylist) |
| /api/playlist | POST | [Create a new playlist](#apiplaylist) |
| /api/playlist/**&lt;playlist_id&gt;** | PUT | [Add a song to a playlist](#apiplaylist) |
| /api/self         | GET | [Returns information about the users](#apiself) |

### /signin
**POST**
*Signs you in*

`{"email": "a@b.com", "password": "supersecretpassword"}`

### /api/catalog/release
**GET**
*Returns all releases/albums available*

### /api/catalog/track
**GET**
*Returns all tracks available*

### /api/playlist
**GET**
*Return all user playlists with tracks*

**POST**
*Create a new playlist*
`{"name":"TESTING","tracks":[]}`

**PUT**
*Add a track to a playlist.*
`{"_id":"56290bf0ddd2cfb810eddae9","name":"Valkyrie","userId":"55fc1f7d53c399fc274c5054","deleted":false,"public":false,"tracks":[{"trackId":"53a0c93640cc048e26f848e6","releaseId":"53a897d07f9a812a0d96bbdc"},{"trackId":"542f2c17502836c00e5be117","releaseId":"542f2bac502836c00e5be116"},{"trackId":"5614507cc5df9f40201f85ed","releaseId":"561c5da57fb673586a3d2a98"},{"trackId":"56e0a83280a64c6105fcc8ec","releaseId":"57083d7e85ff0545443034e3","startTime":0}]}`

### /api/playlist/**&lt;playlist_id&gt;**
**PUT**
*Rename playlist or make public*
`{"_id":"5725bc898fcb2ef579fe5f9d","name":"TESTINGRENAME","userId":"55fc1f7d53c399fc274c5054","deleted":false,"public":false,"tracks":[]}`

**DELETE**
*Remove playlist*

### /api/self
### /api/self/session
**GET**
*Returns info about the account when logged in*



# Connect API official doc

Write Date: April 14, 2016  
Last Update: July 5, 2016  
API Version Prefix: `/api`

## Routes

### GET `/catalog/track`

Gets all tracks. You can use default collection query options.

**WARNING**

> Even though this route is publicly available it may not be available in future releases. It is advised to fetch releases and their tracks. See below.

### GET `/catalog/track/:id`

Gets a track by id.

### GET `/catalog/release`

Gets all releases. You can use default collection query options.

### GET `/catalog/release/:catalog_id`

Gets a release by id OR catalog id.

### GET `/catalog/release/:id/tracks`

Gets you tracks for a release. You can use default collection query options.

### GET `/catalog/artist`

Gets you artists.

### GET `/catalog/artist/:vanity_uri`

Gets you an artist by id or their vanity URI

### GET `/catalog/artist/:vanity_uri/releases`

Gets you an artists releases.

### GET `/playlist`

Gets you a playlist that is publicly available.

### GET `/playlist/:id/tracks`

Gets you tracks for a playlist. You can use default collection query options.

## Query Options

Query options are simple URL query string key values.

### Collections

#### Fields

`fields=a,b,c`

Specifiy what fields you wish to recieve by a comma separated string.

**WARNING**

> Some fields are manatory and will appear anyways.

#### Identifiers

`ids=id1,id2`

Specifies specific ids you want to fetch instead of the whole collection.

#### Offset/Skip

`skip=100`

Specifies the starting point of the collection you wish to fetch.

#### Amount/Limit

`limit=10`

Specifies the number of results you wish to fetch.

**WARNING**

> In the future it may be capped.

#### Fuzzy Match

`fuzzy=field,value,field2,value2`

Specifies searches with fuzzy matching. This is an AND operation. Use `fuzzyOr` for OR operations.  
The parameter value is a comma separated pair list.

#### Filter Match

`filter=field,value,field2,value2`

Specifies searches with exact matching. This is an AND operation. Use `filterOr` for OR operations.  
The parameter value is a comma separated pair list.

