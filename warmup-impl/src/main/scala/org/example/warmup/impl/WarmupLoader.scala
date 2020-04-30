package org.example.warmup.impl

import akka.actor.ActorSystem
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire._
import org.example.warmup.api.WarmupService
import play.api.libs.ws.ahc.AhcWSComponents

class WarmupLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new WarmupApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new WarmupApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[WarmupService])
}

abstract class WarmupApplication(context: LagomApplicationContext)
    extends LagomApplication(context)
    with CassandraPersistenceComponents
    with AhcWSComponents {

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[WarmupService](wire[WarmupServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = WarmupSerializerRegistry

  // Register the Warmup persistent entity
  persistentEntityRegistry.register(wire[WarmupEntity])

  // warmup by starting some dummy entites
  Warmup(actorSystem)

}
