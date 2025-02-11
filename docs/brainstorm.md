# Key Point for ECIBot functionality
In this document, we will write down all functionality of the current Python implementation and some new ideas in order
to create the roadmap of the bot.

## Index

<!-- TOC -->
* [Key Point for ECIBot functionality](#key-point-for-ecibot-functionality)
  * [Index](#index)
  * [Basic Functionality](#basic-functionality)
  * [Slash Commands](#slash-commands)
  * [Detailed functionality](#detailed-functionality)
    * [sounds](#sounds)
    * [play](#play)
    * [skip](#skip)
    * [queue](#queue)
    * [tts](#tts)
    * [ask](#ask)
    * [search](#search)
    * [dalle](#dalle)
    * [confetti](#confetti)
    * [mix](#mix)
    * [disconnect](#disconnect)
    * [radio](#radio)
      * [radio play](#radio-play)
      * [radio list](#radio-list)
      * [Extra radio commands](#extra-radio-commands)
    * [Add language support](#add-language-support)
    * [Create lang file](#create-lang-file)
    * [Create constants file for the bot.](#create-constants-file-for-the-bot)
    * [Text Processors](#text-processors)
    * [Usage Tracking](#usage-tracking)
    * [Create player](#create-player)
<!-- TOC -->

## Basic Functionality
- Play music from different services thanks to yt-dlp.
- Play customs sounds uploaded by the server members.
- Play custom messages via tts.
- Generate custom images using Dall-e mini API.
- Generate custom text using OpenAI API.
- All legacy commands will be removed.
- Add multiple queues. Each will be added to given Guild where the command is executed.
- Read data file only once when bot is loaded instead of re-reading each time is needed.
- Add language support.
- Create lang file.
- Create constants file for the bot.

## Slash Commands
- /sounds: Shows the list of custom sounds and the quantity available. It will be shown in a embed message with buttons paginated. Each page will show at most 100 sounds.
- /play <name or url>: Plays the sound with that name or the specified url. This url can be direct or from services supported by yt-dlp, such as YouTube or Twitter. This command can add multiple sounds or urls for each command.
- /skip: Skips the current sound playing. If there isn't more on the queue, the bot will disconnect from the voice channel.
- /queue: Shows current queue.
- /tts <prompt>: Generates a tts sound with the given message.
- /ask <prompt>: Sends the prompt to OpenAI's API and generate a tts sound with the answer given.
- /search <local/youtube/youtubemusic/soundcloud> <prompt>: Searches local sounds that contains that prompt or searches it in the specified service and plays the first result. In the case of YouTube Music you can use hashtags to specify the type of content.
- /dalle <prompt>: Generates 9 images in a 3 by 3 array by sending the given prompt to Dall-e mini API. It may take up to a minute to get the images from the API.
- /confetti <number>: 🎉 Plays the specified number of random Confetti songs.
- /mix <youtube/youtubemusic/soundcloud> <search query or url>: Plays a mix generated from the given search or url.
- /disconnect: disconnects the bot and clears Guild's queue.
- /radio play <radio name>: Play's given radio station if exists. It will be autocompleted with all autofetched radios and with user's given ones.
- /radio list: Shows all available radio stations.

## Detailed functionality
### sounds
Shows the list of custom sounds and the quantity available. It will be shown in a embed message with buttons paginated. Each page will show at most 100 sounds.

This embed will be almost the same as the actual implemented on current ECIBot Python but with the pagination.

### play
Plays the sound with that name or the specified url. This url can be direct or from services supported by yt-dlp, such as YouTube or Twitter. This command can add multiple sounds or urls for each command.

Mantain support on the context menu command (right click a message) in order to play given url, urls or sounds.

This command will interact with the Guild's queue.

It should show only one message once all sounds are resolved. Url's direct download will be resolved when played inside Guild's queue instead of resolving before hand. This will take a impact when playing the next sound, but will ensure every sound is played. Local sounds will be resolved in this step.

- (?) Resolve before hand and add a catch if the sound's direct url doesn't work anymore, resolving it again. This will require saving both original url and the resolved one.

- (?) Add support to Spotify's urls and then search the result with this information on the other services.

### skip
Skips the current sound playing. If there isn't more on the queue, the bot will disconnect from the voice channel. It will work most likely like current Python's implementation.

### queue
Shows current guild's queue. This command will be paginated, showing the first n elements and having only one field on the embed.

This command could group every equal sound that are one after the other. For example: if there are 5 `a` sounds one after the other, this command may show `a (x5)` or something like this.

### tts

Generates a tts sound with the given message.

It will be needed to wrap current Python implementation for gTTS, as it may be only for Python. Loquendo's implementation will be ported to Kotlin using KTOR-Client.

Mantain support on the context menu command (right click a message) in order to read the content of given message.

### ask
Sends the prompt to AI's API and generate a tts sound with the answer given.

As of right now, this will be using HuggingFace API, as OpenAI have closed their free credits program. Now, this ask command is working with a chat interface, so we could add some extra functionality like addind a flag to convert current thread into a chat with whichever model we are using.

Given prompt should be displayed inside the thread.

Mantain support on the context menu command (right click a message) in order to generate a response with the content of given message.

### search

Searches local sounds that contains that prompt or searches it in the specified service and plays the first result. In the case of YouTube Music you can use hashtags to specify the type of content.

Mantain support on the context menu command (right click a message) in order to search given url on given service.

Maybe, we could add extra functionality in order to give options when being searched on a service (Youtube, Youtube Music, SoundCloud, Spotify?).

### dalle

Generates 9 images in a 3 by 3 array by sending the given prompt to Dall-e mini API. It may take up to a minute to get the images from the API.

As of right now, it will work mostly like Python's implementation.

- (?) Search for other alternatives in order to get better images.
- (?) Video generation.

### confetti

Plays the specified number of random Confetti songs. It will work exactly like current implementation in Python with the improvements on play command.

### mix
Plays a mix generated from the given search or url. It will work exactly like current implementation in Python with the improvements on play command.

- (?) Being able to stop this proccess.

### disconnect
Disconnects the bot and clears Guild's queue. This command should clear all sounds being proccess.

Mantain support on the context menu command (right click the bot) in order to disconnect it from Voice Chat.

### radio
#### radio play
Play's given radio station if exists. It will be autocompleted with all autofetched radios and with user's given ones.

This command will be ported almost like current implementation, as it already autocompletes.

- (?) Save user's preferences and show them first, then autocomplete with other radio stations.

#### radio list
Shows all available radio stations. This command should be exactly like the current implementation, as it will already control pagination.

#### Extra radio commands

- Create `radio add` in order to support the addition of new radio stations given by users.
- Create `radio remove` in order to remove radio stations added by users.
- Create `radio update` in order to update a name or url of a radio station.
- Create a command to add radio stations as favorite.
- Create a command to remove radio stations as favorite.
- Create a command to check all favourites radio.

### Add language support
With a command, or by fetching guild's language, give the option to have the bot with it's own language on each guild.

- (?) Language preferences for each user.

### Create lang file
Create a file that contains all strings of the bot and can be loaded by demand in order to translate each string to a given language.

### Create constants file for the bot.
Right now, all values are on each file lost or in code. We should have them on one file or on a couple of them inside the same package in order to organize a little more the code.

### Text Processors
Right now, we are processing each message in search of `Twitter` and `Instagram` urls and change them to their `fixup` variant. This functionality should stay the same, but for `Instagram` we should check only valid paths.

- (?) Search and add support for Reddit.
- (?) Search how to add real support to TikTok.

### Usage Tracking
Keep usage tracking in order to display it on ECIBot's website, but adding a command to opt-out of this functionality.

### Create player
Create a player that can be showed by using a command.

This player will be an embed and will display info of current playing sound, the next sound on Guild's queue and some buttons in order to play/pause, skip and stop.

- (?) Add support to play previous sound.
- (?) Create a channel to have this embed static there and it will be updated when new sound is playing.
- (?) Add info on current time and max time of the sound. For example: `(1:50 -------- 3:00)`.