package com.santyramos.mirador.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * El reproductor tal como lo ve la notificación, la pantalla de bloqueo y los audífonos:
 * siempre ofrece «Anterior» y «Siguiente», y los manda a [VideoController] (cola, lista o relacionados).
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ControlesSesion(player: Player) : ForwardingPlayer(player) {
    private val extras = setOf(
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    )

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(*extras.toIntArray()).build()

    override fun isCommandAvailable(command: Int): Boolean = command in extras || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = VideoController.siguiente()
    override fun seekToNextMediaItem() = VideoController.siguiente()
    override fun seekToPrevious() = VideoController.anterior()
    override fun seekToPreviousMediaItem() = VideoController.anterior()
}
