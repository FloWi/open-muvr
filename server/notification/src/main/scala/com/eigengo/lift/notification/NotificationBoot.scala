package com.eigengo.lift.notification

import akka.actor.{ActorRef, ActorSystem}
import com.eigengo.lift.common.MicroserviceApp.BootedNode

case class NotificationBoot(notification: ActorRef) extends BootedNode

object NotificationBoot {

  def boot(userProfile: ActorRef)(implicit system: ActorSystem): NotificationBoot = {
    val notification = system.actorOf(Notification.props(userProfile), Notification.name)
    NotificationBoot(notification)
  }

}