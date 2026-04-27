package io.estatico.newtype.macros.pkgobj

import io.estatico.newtype.macros.newtype

/*
 * Fixture: a nested package object inside a package whose parent declares a `package object`.
 *
 * Mirrors the `package object common.crm { @newtype case class CrmObject(value: RefinedSaneString) }`
 * pattern, where `RefinedSaneString` lives in the parent `package object common`.
 *
 * Two pieces are exercised together:
 *   - the parent's package-object types must remain in scope inside this nested package, and
 *   - `@newtype` expansion must work inside a nested package object's body too.
 *
 * If the plugin loses the `Package` flag on the parent's package object, both fail: the parent is
 * emitted as a regular object, the package `pkgobj.nested` cannot exist underneath a class named
 * `pkgobj`, and `SaneString` is no longer reachable here without a fully-qualified import.
 */
package object nested {

  @newtype final case class SubField(value: SaneString)
  object SubField {
    val Sentinel: SubField = SubField("sentinel")
  }

  @newtype final case class SubLabel(value: SaneString)
}