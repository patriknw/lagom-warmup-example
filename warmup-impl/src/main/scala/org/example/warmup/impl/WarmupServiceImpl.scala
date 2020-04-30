package org.example.warmup.impl

import scala.concurrent.ExecutionContext

import akka.NotUsed
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.PersistentEntityRegistry
import org.example.warmup.api.WarmupService

/**
 * Implementation of the WarmupService.
 */
class WarmupServiceImpl(clusterSharding: ClusterSharding, persistentEntityRegistry: PersistentEntityRegistry)(
    implicit ec: ExecutionContext)
    extends WarmupService {

  override def hello(id: String): ServiceCall[NotUsed, String] = ServiceCall { _ =>
    // Look up the Warmup entity for the given ID.
    val ref = persistentEntityRegistry.refFor[WarmupEntity](id)

    // Ask the entity the Hello command.
    ref.ask(Hello(id))
  }

  override def useGreeting(id: String) = ServiceCall { request =>
    // Look up the Warmup entity for the given ID.
    val ref = persistentEntityRegistry.refFor[WarmupEntity](id)

    // Tell the entity to use the greeting message specified.
    ref.ask(UseGreetingMessage(request.message))
  }

}
