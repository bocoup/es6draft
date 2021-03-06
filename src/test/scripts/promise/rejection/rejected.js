/*
 * Copyright (c) 2012-2016 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */

{
  let pending;
  let p1 = new Promise((resolve, reject) => { pending = {resolve, reject} });
  let p2 = p1.catch(() => {});
  pending.reject(new Error);
}

{
  let pending;
  let p1 = new Promise((resolve, reject) => { pending = {resolve, reject} });
  let p2 = p1.then(() => {});
  let p3 = p2.catch(() => {});
  pending.reject(new Error);
}

{
  let pending;
  let p1 = new Promise((resolve, reject) => { pending = {resolve, reject} });
  let p2 = p1.then(() => {});
  let p3 = p1.catch(() => {});
  pending.reject(new Error);
}

{
  let pending;
  let p1 = new Promise((resolve, reject) => { pending = {resolve, reject} });
  let p2 = p1.then(() => {});
  let p3 = p2.catch(() => {});
  Promise.resolve().then().then().then(() => { pending.reject(new Error) });
}

triggerGC();
