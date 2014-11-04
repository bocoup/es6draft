/*
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */

const {
  assertThrows
} = Assert;

// Indexed access on uninitialized typed array throws TypeError from currently active realm

const foreignRealm = new Reflect.Realm();
foreignRealm.eval(`
  function indexedAccess(ta) {
    ta[0];
  }
`);

let int8Array = new class extends Int8Array {constructor() { /* no super */ }};
assertThrows(TypeError, () => int8Array[0]);
assertThrows(foreignRealm.global.TypeError, () => foreignRealm.global.indexedAccess(int8Array));
