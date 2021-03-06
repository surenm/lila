package lila.tournament

import scala.concurrent.duration._

import akka.actor.{ ActorRef, ActorSystem, ActorSelection }

import chess.Color
import lila.game.{ Game, Player => GamePlayer, GameRepo, Pov, PovRef, Source, PerfPicker }
import lila.hub.actorApi.map.Tell
import lila.round.actorApi.round.NoStartColor
import lila.user.{ User, UserRepo }

final class AutoPairing(
    roundMap: ActorRef,
    system: ActorSystem,
    secondsToMove: Int) {

  def apply(tour: Started)(pairing: Pairing): Fu[Game] = for {
    user1 ← getUser(pairing.user1)
    user2 ← getUser(pairing.user2)
    game1 = Game.make(
      game = chess.Game(
        board = chess.Board init tour.variant,
        clock = tour.clock.chessClock.some
      ),
      whitePlayer = GamePlayer.white,
      blackPlayer = GamePlayer.black,
      mode = tour.mode,
      variant = tour.variant,
      source = Source.Tournament,
      pgnImport = None
    )
    game2 = game1
      .updatePlayer(Color.White, _.withUser(user1.id, PerfPicker.mainOrDefault(game1)(user1.perfs)))
      .updatePlayer(Color.Black, _.withUser(user2.id, PerfPicker.mainOrDefault(game1)(user2.perfs)))
      .withTournamentId(tour.id)
      .withId(pairing.gameId)
      .start
    _ ← (GameRepo insertDenormalized game2) >>-
      scheduleIdleCheck(PovRef(game2.id, Color.White), secondsToMove)
  } yield game2

  private def getUser(username: String): Fu[User] =
    UserRepo named username flatMap {
      _.fold(fufail[User]("No user named " + username))(fuccess)
    }

  private def scheduleIdleCheck(povRef: PovRef, in: Int) {
    system.scheduler.scheduleOnce(in seconds)(idleCheck(povRef))
  }

  private def idleCheck(povRef: PovRef) {
    GameRepo pov povRef foreach {
      _.filter(_.game.playable) foreach { pov =>
        pov.game.playerHasMoved(pov.color).fold(
          (pov.color.white && !pov.game.playerHasMoved(Color.Black)) ?? {
            scheduleIdleCheck(!pov.ref, pov.game.lastMoveTimeInSeconds.fold(secondsToMove) { lmt =>
              lmt - nowSeconds + secondsToMove
            })
          },
          roundMap ! Tell(pov.gameId, NoStartColor(pov.color))
        )
      }
    }
  }
}
