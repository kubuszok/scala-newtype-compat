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

  it should "work with qualified annotation" in {
    val x: QualifiedFoo = QualifiedFoo("hello")
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

  it should "merge with existing companion object" in {
    WithCompanion(42) shouldBe WithCompanion.create
    WithCompanion.hello shouldBe "hello"
  }

  behavior of "@newsubtype"

  it should "create a simple newsubtype" in {
    val x: Sub = Sub(42)
    val y: Int = x.coerce[Int]
    y shouldBe 42
  }

  it should "work with qualified annotation" in {
    val x: QualifiedSub = QualifiedSub(7)
    x.coerce[Int] shouldBe 7
  }

  it should "support Coercible wrapping/unwrapping" in {
    val w = implicitly[Coercible[Int, Foo]]
    val u = implicitly[Coercible[Foo, Int]]
    val foo = w(42)
    val int = u(foo)
    int shouldBe 42
  }

  it should "support coercing with .coerce" in {
    val foo = 42.coerce[Foo]
    val int = foo.coerce[Int]
    int shouldBe 42
  }
}

object NewTypeMacrosTest {
  trait Show[A] {
    def show(a: A): String
  }

  @newtype case class Foo(x: Int)

  @io.estatico.newtype.macros.newtype case class QualifiedFoo(value: String)

  @newtype case class Baz[A](xs: List[A])

  @newtype case class HasMethods(value: Int) {
    def plus(other: Int): HasMethods = HasMethods(value + other)
  }

  @newtype(unapply = true) case class WithUnapply(x: Int)

  @newtype case class WithCompanion(x: Int)
  object WithCompanion {
    val create: WithCompanion = WithCompanion(42)
    val hello: String = "hello"
  }

  @newsubtype case class Sub(x: Int)

  @io.estatico.newtype.macros.newsubtype case class QualifiedSub(x: Int)
}
