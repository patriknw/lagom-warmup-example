package org.example.warmup.impl

import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.control.NonFatal

import akka.Done
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.Props
import akka.actor.Timers
import akka.cluster.sharding.ClusterSharding
import com.lightbend.lagom.scaladsl.persistence.CommandEnvelope
import org.slf4j.LoggerFactory

object Warmup extends ExtensionId[Warmup] with ExtensionIdProvider {
  override def get(system: ActorSystem): Warmup = super.get(system)

  override def lookup = Warmup

  override def createExtension(system: ExtendedActorSystem): Warmup = new Warmup(system)
}

class Warmup(system: ActorSystem) extends Extension {
  private val log = LoggerFactory.getLogger(getClass)

  private val _ready = Promise[Done]()

  def ready: Future[Done] = _ready.future

  private val entityTypes = List("WarmupEntity")

  private val allReady = entityTypes.map { entityTypeName =>
    val p = Promise[Done]()
    system.actorOf(WarmupSender.props(entityTypeName, p), s"warmup$entityTypeName")
    p.future
  }
  import system.dispatcher
  _ready.completeWith(Future.sequence(allReady).map(_ => Done))

  ready.onComplete { result =>
    log.info("Warmup of [{}] completed: {}", entityTypes.mkString(", "), result)
  }

}

object WarmupSender {

  def props(entityTypeName: String, ready: Promise[Done]): Props =
    Props(new WarmupSender(entityTypeName, ready))
}

class WarmupSender(entityTypeName: String, ready: Promise[Done]) extends Actor with ActorLogging with Timers {

  private val entityIds = {
    import scala.collection.JavaConverters._
    context.system.settings.config.getStringList(s"warmup.$entityTypeName.entity-ids").asScala
  }
  private var remainingResponses = entityIds.toSet

  timers.startTimerAtFixedRate("tick", "tick", 100.millis)

  // in case no entities configured
  stopWhenAllStarted()

  override def receive: Receive = {
    case "tick" =>
      try {
        val region = ClusterSharding(context.system).shardRegion(entityTypeName)
        // less frequent repeat
        timers.startTimerAtFixedRate("tick", "tick", 3.seconds)
        log.info("Sending StartEntity to [{}] entities of type {}.", remainingResponses.size, entityTypeName)
        // In future versions of Lagom this could use ShardRegion.StartEntity instead if
        // we backport https://github.com/lagom/lagom/pull/2754
        // Then it would be without the CommandEnvelope.
        remainingResponses.foreach { entityId =>
          region ! CommandEnvelope(entityId, StartEntity(entityId))
        }
      } catch {
        case NonFatal(e) =>
          // not initialized yet, will repeat
          log.info(e.getMessage)
      }

    case StartEntityAck(entityId) =>
      log.info("{} entityId [{}] started", entityTypeName, entityId)
      remainingResponses -= entityId
      stopWhenAllStarted()
  }

  private def stopWhenAllStarted(): Unit = {
    if (remainingResponses.isEmpty) {
      log.info("All {} started", entityTypeName)
      ready.trySuccess(Done)
      context.stop(self)
    }
  }
}

final class WarmupHealthCheck(system: ActorSystem) extends (() => Future[Boolean]) {
  private val log = LoggerFactory.getLogger(getClass)

  override def apply(): Future[Boolean] = {
    log.debug("WarmupHealthCheck called")
    import system.dispatcher
    Warmup(system).ready.map(_ => true)
  }
}
