# Monstercat Connect API

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
| /api/self/session         | GET | [Returns information about the users](#apiselfsession) |
| /api/self/referral-code   | GET | [Redirects you](#apiselfreferral-code) |
| /api/user/**&lt;user_id&gt;**/referral-code| GET | [?](#apiuseruser_idreferral-code) |
| /subscription/payments/**&lt;user_id&gt;** | GET | [Returns all payments of the user](#subscriptionpaymentsuser_id) |

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


### /api/self/session
**GET**
*Returns info about the account when logged in*

### /api/user/**&lt;user_id&gt;**/referral-code
**GET**
*Returns ?. Empty JSON object for me.*

### /subscription/payments/**&lt;user_id&gt;**
**GET**
*Returns all payments for the user*
