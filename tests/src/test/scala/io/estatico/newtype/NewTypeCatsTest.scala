package io.estatico.newtype

import io.estatico.newtype.macros._
import io.estatico.newtype.ops._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats._
import cats.syntax.all._

class NewTypeCatsTest extends AnyFlatSpec with Matchers {

  import NewTypeCatsTest._

  behavior of "@newtype with cats"

  it should "derive Eq instance" in {
    implicit val eqInt: Eq[Int] = Eq.fromUniversalEquals
    val eqFoo: Eq[Foo] = Foo.deriving[Eq]
    eqFoo.eqv(Foo(1), Foo(1)) shouldBe true
    eqFoo.eqv(Foo(1), Foo(2)) shouldBe false
  }

  it should "derive Show instance" in {
    implicit val showInt: Show[Int] = Show.fromToString
    val showFoo: Show[Foo] = Foo.deriving[Show]
    showFoo.show(Foo(42)) shouldBe "42"
  }

  it should "coerce with .coerce" in {
    val foo: Foo = 42.coerce[Foo]
    val i: Int = foo.coerce[Int]
    i shouldBe 42
  }
}

object NewTypeCatsTest {
  @newtype case class Foo(x: Int)
}
