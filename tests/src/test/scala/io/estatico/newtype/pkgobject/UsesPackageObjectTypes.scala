package io.estatico.newtype.pkgobject

import java.util.UUID

final case class UsesPackageObjectTypes(
  tableId: ClayTableId,
  fieldName: ClayFieldName
)
