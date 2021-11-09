
# DEPRECIATED! Please see [the new documentation](https://github.com/defvs/connect-v2-docs)

# Connect API (Alpha)

Base URL: `https://connect.monstercat.com/api`

## Routes

### /catalog/browse

**GET**
Returns tracks paired with their releases.
Tracks that are on two releases will be returned twice.

#### Browse Query Parameters

|Param|Description|
|:--|:--|
|search|Does a text search on track title, artists, and albums|
|playlistId|Will only return tracks on provided playlist|
|albumId|Only returns tracks found on that album|
|isrc|Return track with that ISRC|
|types|Comma separated list of album types. Options: Single, EP, Podcast, Album|
|genres|Comma separated list of album genres.|
|tags|Comma separate list of track tags.|
|sortOn|Field to sort on. Options: `title`, `release` (album title), `bpm`, `time` (track duration), `date` (album release date), `artists` (artistsTitle field)|
|sortDirection|-1 for descending, 1 for ascending.|

### /catalog/track

**GET**
Returns all tracks - you can use default collection query options.

**WARNING**
> Even though this route is publicly available, it may not be available in future releases.
> It is advised to fetch releases and their tracks. See below.

### /catalog/track/:id

**GET**
Returns a track by id.

### /catalog/release

**GET**
Returns all releases - you can use default collection query options

### /catalog/release/:catalog_id

**GET**
Returns a release by id OR catalog id

### /catalog/release/:id/tracks

**GET**
Returns tracks for a release - you can use default collection query options

### /catalog/artist

**GET**
Returns all artists - you can use default collection query options

### /catalog/artist/:vanity_uri

**GET**
Returns an artist by id or their vanity URI

### /catalog/artist/:vanity_uri/releases

**GET**
Returns an artists releases

### /playlist
*Requires you to be logged in!*

**GET**
Returns your playlists

**POST**
Create a new playlist
```json
{
  "name":"New Playlist",
  "tracks":[]
}
```

**PUT**
Add a track to a playlist
```json
{
  "_id": "56290bf0ddd2cfb810eddae9",
  "name": "Valkyrie",
  "userId": "55fc1f7d53c399fc274c5054",
  "deleted": false,
  "public": false,
  "tracks": [{"trackId":"5614507cc5df9f40201f85ed","releaseId":"561c5da57fb673586a3d2a98"},{"trackId":"56e0a83280a64c6105fcc8ec","releaseId":"57083d7e85ff0545443034e3","startTime":0}]
}
```

### /playlist/:id
*Requires you to be logged in!*

**PUT**
Rename playlist or make it public

```json
{
  "_id": "5725bc898fcb2ef579fe5f9d",
  "name": "New Playlist Name",
  "userId": "55fc1f7d53c399fc274c5054",
  "deleted": false,
  "public": false,
  "tracks": []
}
```

**DELETE**
Delete this playlist

### /playlist/:id/tracks

**GET**
Returns tracks for a playlist - you can use default collection query options

### /self
*Requires you to be logged in!*

**GET**
Returns information about your account

### /self/session
*Requires you to be logged in!*

**GET**
Returns information about your current session

## Query Options

Query options are simple URL query string key values.

### Collections

#### Fields 

`fields=a,b,c`

Specifies the fields you wish to receive by a comma separated string.

**WARNING**
> Some fields are mandatory and will appear anyways.

#### Identifiers 

`ids=id1,id2`

Specifies specific ids you want to fetch instead of the whole collection.  
This parameter is not available on the `/catalog/browse` route.

#### Offset/Skip 

`skip=100`

Specifies the starting point of the collection you wish to fetch.

#### Amount/Limit 

`limit=10`

Specifies the number of results you wish to fetch.

**WARNING**
> In the future it may be capped.

#### Fuzzy Match

`fuzzy=field1,value1,field2,value2`

Specifies searches with fuzzy matching. This is an AND operation. Use `fuzzyOr` for OR operations.  
The parameter value is a comma separated pair list.  
This parameter is not available on the `/catalog/browse` route.


#### Filters

`filters=field1,value1,field2,value2`

Specifies searches with exact matching. Case sensitive. This is an AND operation. Use `filtersOr` for OR operations.  
The parameter value is a comma separated pair list.  
This parameter is not available on the `/catalog/browse` route. 
