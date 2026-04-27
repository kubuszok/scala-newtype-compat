package io.estatico.newtype.macros

import io.estatico.newtype.ops._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/*
 * Tests for `@newtype` declarations placed directly inside a `package object` body, with and
 * without companions, plus a nested `package object` that depends on its parent's declarations.
 *
 * Fixtures live in:
 *   - tests/.../pkgobj/package.scala
 *   - tests/.../pkgobj/nested/package.scala
 *
 * On Scala 3, the rebuilt `ModuleDef` for a package object drops the `Package` modifier, so the
 * package object is emitted as a regular object. The runtime tests below illustrate the user-
 * visible consequences (nested package inaccessible, parent type alias not in scope from the
 * nested package). They are expected to pass once the plugin preserves package-object identity.
 */
class NewTypeInPackageObjectTest extends AnyFlatSpec with Matchers {

  behavior of "@newtype inside a package object"

  it should "expand a @newtype with a companion containing typeclass instances" in {
    val t = pkgobj.Tenant("acme")
    t.coerce[String] shouldBe "acme"
    pkgobj.Tenant.Default.coerce[String] shouldBe "default"
    pkgobj.Tenant.of("foo").coerce[String] shouldBe "foo"
    val ord = implicitly[Ordering[pkgobj.Tenant]]
    ord.compare(pkgobj.Tenant("a"), pkgobj.Tenant("b")) should be < 0
  }

  it should "expand a @newtype whose companion only declares vals" in {
    pkgobj.User.Anonymous.coerce[String] shouldBe "anonymous"
  }

  it should "expand a @newtype that has no companion at all" in {
    pkgobj.JobName("nightly").coerce[String] shouldBe "nightly"
  }

  behavior of "@newtype inside a nested package object"

  it should "resolve type aliases declared in the parent package object" in {
    pkgobj.nested.SubField("x").coerce[String] shouldBe "x"
    pkgobj.nested.SubField.Sentinel.coerce[String] shouldBe "sentinel"
  }

  it should "expand a @newtype without a companion in a nested package object" in {
    pkgobj.nested.SubLabel("y").coerce[String] shouldBe "y"
  }
}