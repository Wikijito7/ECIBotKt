package es.wokis.exceptions

class EmptyDiscordTokenException : IllegalArgumentException("Discord token shouldn't be empty")
class EmptyDeezerMasterDecryptionKeyException : IllegalArgumentException("Deezer is enabled but no master decryption key was provided")
