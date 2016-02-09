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

// Delta compared to Unicode 5.1
test(0x048a, 0x0527);
test(0x0620, 0x063f);
test(0x0800, 0x0815);
test(0x081a, 0x081a);
test(0x0824, 0x0824);
test(0x0828, 0x0828);
test(0x0840, 0x0858);
test(0x0972, 0x0977);
test(0x0979, 0x097f);
test(0x0cf1, 0x0cf2);
test(0x0d12, 0x0d3a);
test(0x0d4e, 0x0d4e);
test(0x0f88, 0x0f8c);
test(0x1100, 0x1248);
test(0x166f, 0x167f);
test(0x18b0, 0x18f5);
test(0x1980, 0x19ab);
test(0x1a20, 0x1a54);
test(0x1aa7, 0x1aa7);
test(0x1bc0, 0x1be5);
test(0x1ce9, 0x1cec);
test(0x1cee, 0x1cf1);
test(0x2090, 0x209c);
test(0x2c60, 0x2c7c);
test(0x2c7e, 0x2ce4);
test(0x2ceb, 0x2cee);
test(0x31a0, 0x31ba);
test(0x4e00, 0x9fcb);
test(0xa4d0, 0xa4f7);
test(0xa4f8, 0xa4fd);
test(0xa640, 0xa66d);
test(0xa6a0, 0xa6e5);
test(0xa6e6, 0xa6ef);
test(0xa78b, 0xa78e);
test(0xa790, 0xa791);
test(0xa7a0, 0xa7a9);
test(0xa7fa, 0xa7fa);
test(0xa8f2, 0xa8f7);
test(0xa8fb, 0xa8fb);
test(0xa960, 0xa97c);
test(0xa984, 0xa9b2);
test(0xa9cf, 0xa9cf);
test(0xaa60, 0xaa6f);
test(0xaa70, 0xaa70);
test(0xaa71, 0xaa76);
test(0xaa7a, 0xaa7a);
test(0xaa80, 0xaaaf);
test(0xaab1, 0xaab1);
test(0xaab5, 0xaab6);
test(0xaab9, 0xaabd);
test(0xaac0, 0xaac0);
test(0xaac2, 0xaac2);
test(0xaadb, 0xaadc);
test(0xaadd, 0xaadd);
test(0xab01, 0xab06);
test(0xab09, 0xab0e);
test(0xab11, 0xab16);
test(0xab20, 0xab26);
test(0xab28, 0xab2e);
test(0xabc0, 0xabe2);
test(0xd7b0, 0xd7c6);
test(0xd7cb, 0xd7fb);
test(0xfa30, 0xfa6d);
test(0x1083f, 0x10855);
test(0x10a60, 0x10a7c);
test(0x10b00, 0x10b35);
test(0x10b40, 0x10b55);
test(0x10b60, 0x10b72);
test(0x10c00, 0x10c48);
test(0x11003, 0x11037);
test(0x11083, 0x110af);
test(0x13000, 0x1342e);
test(0x16800, 0x16a38);
test(0x1b000, 0x1b001);
test(0x2a700, 0x2b734);
test(0x2b740, 0x2b81d);
