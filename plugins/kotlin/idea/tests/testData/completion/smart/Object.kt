trait I

object O : I

fun foo(): I = <caret>

// EXIST: { lookupString: "O", itemText: "O", tailText: " (<root>)" }
// EXIST: foo
// EXIST: object
// NUMBER: 3
