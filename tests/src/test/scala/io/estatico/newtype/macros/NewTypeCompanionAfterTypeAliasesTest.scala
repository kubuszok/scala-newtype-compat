package io.estatico.newtype.macros

import io.estatico.newtype.ops._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/*
 * Regression: a `@newtype` declaration followed by `type` aliases, and then its companion object.
 *
 * On Scala 2 this is fine because macro paradise looks up the companion symbol via the typer
 * regardless of intervening declarations. The original Scala 3 plugin only inspected the
 * immediately-next stat (`statsList(i + 1)`) for a companion, so this idiom (common inside
 * `package object` blocks where helper aliases are colocated with the newtype) caused the
 * companion to be missed entirely. The synthesized companion and the original then coexisted as
 * two `object Foo` declarations, producing `is already defined` errors.
 */
class NewTypeCompanionAfterTypeAliasesTest extends AnyFlatSpec with Matchers {

  import NewTypeCompanionAfterTypeAliasesTest._

  behavior of "@newtype companion separated from declaration by type aliases"

  it should "still merge the companion that follows after intervening `type` decls" in {
    val id = OrderId("ord-1")
    id.coerce[String] shouldBe "ord-1"
    OrderId.fromString("ord-2").coerce[String] shouldBe "ord-2"
    OrderId.Default.coerce[String] shouldBe "ord-default"
  }
}

object NewTypeCompanionAfterTypeAliasesTest {
  @newtype final case class OrderId(value: String)
  // Intervening type aliases — must not prevent the companion below from being recognized.
  type OrderIdHelper = String
  type OrderIdAnotherHelper = OrderIdHelper
  object OrderId {
    def fromString(s: String): OrderId = OrderId(s)
    val Default: OrderId = OrderId("ord-default")
  }
}