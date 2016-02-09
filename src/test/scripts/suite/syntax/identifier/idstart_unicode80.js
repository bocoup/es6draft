/*
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */

function test(start, end) {
  for (let cp = start; cp <= end;) {
    let source = "var obj = {};\n";
    for (let i = 0; cp <= end && i < 1000; ++cp, ++i) {
      source += `obj.${String.fromCodePoint(cp)};\n`;
    }
    eval(source);
  }
}

// Delta compared to Unicode 7.0
test(0x08a0, 0x08b4);
test(0x0af9, 0x0af9);
test(0x0c58, 0x0c5a);
test(0x0d5f, 0x0d61);
test(0x13a0, 0x13f5);
test(0x13f8, 0x13fd);
test(0x19b0, 0x19c9);
test(0x4e00, 0x9fd5);
test(0xa78f, 0xa78f);
test(0xa7b0, 0xa7b7);
test(0xa8fd, 0xa8fd);
test(0xab60, 0xab65);
test(0xab70, 0xabbf);
test(0x108e0, 0x108f2);
test(0x108f4, 0x108f5);
test(0x10c80, 0x10cb2);
test(0x10cc0, 0x10cf2);
test(0x111dc, 0x111dc);
test(0x11280, 0x11286);
test(0x11288, 0x11288);
test(0x1128a, 0x1128d);
test(0x1128f, 0x1129d);
test(0x1129f, 0x112a8);
test(0x11350, 0x11350);
test(0x115d8, 0x115db);
test(0x11700, 0x11719);
test(0x12000, 0x12399);
test(0x12480, 0x12543);
test(0x14400, 0x14646);
test(0x2b820, 0x2cea1);