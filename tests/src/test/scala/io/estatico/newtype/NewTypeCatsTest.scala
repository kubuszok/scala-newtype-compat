package io.estatico.newtype

import io.estatico.newtype.macros._
import io.estatico.newtype.ops._
import cats.{Eq, Order, Show}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NewTypeCatsTest extends AnyFlatSpec with Matchers {

  import NewTypeCatsTest._

  behavior of "@newtype with cats"

  it should "derive Eq instance" in {
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

  it should "resolve Order.by(_.value) in companion" in {
    val ord = implicitly[Order[Name]]
    ord.compare(Name("Alice"), Name("Bob")) should be < 0
    ord.compare(Name("Bob"), Name("Alice")) should be > 0
    ord.compare(Name("Alice"), Name("Alice")) shouldBe 0
  }

  it should "resolve Show.show(_.value) in companion" in {
    val show = implicitly[Show[Name]]
    show.show(Name("Alice")) shouldBe "Alice"
  }

  it should "resolve Eq.by(_.value) in companion" in {
    val eq = implicitly[Eq[Score]]
    eq.eqv(Score(10), Score(10)) shouldBe true
    eq.eqv(Score(10), Score(20)) shouldBe false
  }
}

object NewTypeCatsTest {
  @newtype case class Foo(x: Int)

  @newtype case class Name(value: String)
  object Name {
    implicit val orderForName: Order[Name] = Order.by(_.value)
    implicit val showForName: Show[Name] = Show.show(_.value)
  }

  @newtype case class Score(value: Int)
  object Score {
    implicit val eqForScore: Eq[Score] = Eq.by(_.value)
  }
}
