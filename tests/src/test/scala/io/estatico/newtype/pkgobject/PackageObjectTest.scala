package io.estatico.newtype.pkgobject

import io.estatico.newtype.ops._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import java.util.UUID

class PackageObjectTest extends AnyFlatSpec with Matchers {

  behavior of "@newtype in package object"

  it should "create a newtype defined in a package object" in {
    val uuid = UUID.randomUUID()
    val x: ClayTableId = ClayTableId(uuid)
    x.value shouldBe uuid
    x.coerce[UUID] shouldBe uuid
  }

  it should "support wrapping via coerce" in {
    val uuid = UUID.randomUUID()
    val x = uuid.coerce[ClayTableId]
    x.value shouldBe uuid
  }

  it should "work with multiple newtypes in the same package object" in {
    val x: ClayFieldName = ClayFieldName("test")
    x.value shouldBe "test"
  }

  it should "make types accessible from other files in the same package" in {
    val uuid = UUID.randomUUID()
    val u = UsesPackageObjectTypes(ClayTableId(uuid), ClayFieldName("field"))
    u.tableId.value shouldBe uuid
    u.fieldName.value shouldBe "field"
  }
}
