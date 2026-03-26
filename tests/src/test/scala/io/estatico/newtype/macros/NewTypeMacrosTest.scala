package io.estatico.newtype.macros

import io.estatico.newtype.Coercible
import io.estatico.newtype.ops._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class NewTypeMacrosTest extends AnyFlatSpec with Matchers {

  import NewTypeMacrosTest._

  behavior of "@newtype"

  it should "create a simple newtype with accessor" in {
    val x: Foo = Foo(1)
    val y: Int = x.coerce[Int]
    y shouldBe 1
    x.x shouldBe 1
  }

  it should "create a newtype with string value" in {
    val x: Bar = Bar("hello")
    x.value shouldBe "hello"
  }

  it should "create a newtype with type parameters" in {
    val x: Baz[Int] = Baz(List(1, 2, 3))
    x.coerce[List[Int]] shouldBe List(1, 2, 3)
  }

  it should "support deriving" in {
    implicit val showInt: Show[Int] = (a: Int) => a.toString
    val showFoo: Show[Foo] = Foo.deriving[Show]
    showFoo.show(Foo(42)) shouldBe "42"
  }

  it should "support instance methods" in {
    val x = HasMethods(10)
    x.plus(5) shouldBe HasMethods(15)
  }

  it should "support unapply when enabled" in {
    val x = WithUnapply(99)
    val result = x match {
      case WithUnapply(n) => n
    }
    result shouldBe 99
  }

  behavior of "@newsubtype"

  it should "create a simple newsubtype" in {
    val x: Sub = Sub(42)
    val y: Int = x.coerce[Int]
    y shouldBe 42
  }
}

object NewTypeMacrosTest {
  trait Show[A] {
    def show(a: A): String
  }

  @newtype case class Foo(x: Int)

  @newtype case class Bar(value: String)

  @newtype case class Baz[A](xs: List[A])

  @newtype case class HasMethods(value: Int) {
    def plus(other: Int): HasMethods = HasMethods(value + other)
  }

  @newtype(unapply = true) case class WithUnapply(x: Int)

  @newsubtype case class Sub(x: Int)
}
