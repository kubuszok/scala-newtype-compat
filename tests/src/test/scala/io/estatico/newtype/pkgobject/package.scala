package io.estatico.newtype

import cats.{Hash, Order, Show}
import io.estatico.newtype.macros.newtype
import java.util.UUID

package object pkgobject {

  @newtype final case class ClayTableId(value: UUID)
  object ClayTableId {
    implicit lazy val showForClayTableId: Show[ClayTableId] = Show.show(_.value.toString())
    implicit lazy val orderForClayTableId: Order[ClayTableId] = Order.by(_.value)
    implicit lazy val hashForClayTableId: Hash[ClayTableId] = Hash.by(_.value)
  }

  @newtype final case class ClayFieldName(value: String)
  object ClayFieldName {
    implicit lazy val showForClayFieldName: Show[ClayFieldName] = Show.show(_.value)
    implicit lazy val orderForClayFieldName: Order[ClayFieldName] = Order.by(_.value)
    implicit lazy val hashForClayFieldName: Hash[ClayFieldName] = Hash.by(_.value)
  }
}
