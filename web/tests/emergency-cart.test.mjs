import test from "node:test";
import assert from "node:assert/strict";
import { inspectEmergencyBackup, isQuotaError, replayEmergency } from "../emergency-cart.js";

const makeEntry = (overrides = {}) => ({
  id: "item-a", title: "Arroz de teste", unit: "un",
  plannedMilli: 1000, actualMilli: 1000,
  estimated: 500, actual: 550, bought: true, ...overrides
});
const basket = (entries = [makeEntry()]) => ({
  market: "Mercado", generation: "fixture-generation",
  revision: 3, budget: 10000, entries
});
const backup = (original = basket()) => ({
  type: "central-do-casal-emergency-backup",
  household: "home-a", uid: "alice", market: "Mercado",
  baskets: { Mercado: original },
  baseRevision: 3, baseline: original,
  operations: [{ type: "budget", budget: 9000 }]
});
const state = (current = basket()) => ({
  uid: "alice", household: "home-a", baskets: { Mercado: current }
});

test("offline backup only becomes sync-safe with a confirmed baseline", () => {
  const source = backup();
  const verified = inspectEmergencyBackup(source, state());
  assert.equal(verified.syncSafe, true);
  assert.equal(verified.total, 550);
  assert.equal(verified.purchasedCount, 1);

  const missingBase = { ...source, baseline: undefined };
  const localOnly = inspectEmergencyBackup(missingBase, state({
    ...basket(), revision: 4
  }));
  assert.equal(localOnly.syncSafe, false);
  assert.equal(localOnly.baseline, null);
});

test("backup cannot be applied to another household or generation", () => {
  assert.throws(
    () => inspectEmergencyBackup(backup(), { ...state(), household: "home-b" }),
    /mesma casa/
  );
  assert.throws(
    () => inspectEmergencyBackup(backup(), state({
      ...basket(), generation: "another-generation"
    })),
    /outra lista/
  );
});

test("concurrent edits cannot be overwritten during replay", () => {
  const original = basket();
  const modified = basket([makeEntry({ title: "Arroz editado no segundo aparelho" })]);
  const draft = {
    baseGeneration: original.generation,
    baseline: original,
    operations: [{ type: "update", id: "item-a", entry: makeEntry({ actual: 600 }) }]
  };
  assert.throws(() => replayEmergency(modified, draft), /outro aparelho/);
  assert.equal(draft.operations[0].entry.actual, 600);
});

test("replaying an add with the same ID does not duplicate products", () => {
  const original = basket([]);
  const draft = {
    baseGeneration: original.generation,
    baseline: original,
    operations: [{ type: "add", entry: makeEntry({ bought: false, actual: null }) }]
  };
  const once = replayEmergency(original, draft);
  const twice = replayEmergency(once, draft);
  assert.equal(once.entries.length, 1);
  assert.equal(twice.entries.length, 1);
});

test("quota errors are recognized without swallowing permission errors", () => {
  assert.equal(isQuotaError({ code: "resource-exhausted" }), true);
  assert.equal(isQuotaError(new Error("Quota exceeded")), true);
  assert.equal(isQuotaError({ code: "permission-denied" }), false);
});
