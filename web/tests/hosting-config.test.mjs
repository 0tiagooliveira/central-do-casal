import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const read = (path) => readFileSync(resolve(root, path), "utf8");

test("Firebase Hosting points to the existing web frontend", () => {
  const config = JSON.parse(read("firebase.json"));
  const projects = JSON.parse(read(".firebaserc"));
  assert.equal(projects.projects.default, "central-do-casal");
  assert.equal(config.hosting.public, "web");
  assert.ok(config.hosting.ignore.includes("**/node_modules/**"));
  assert.match(read("web/index.html"), /<script type="module" src="main\.js"><\/script>/);
  assert.match(read("web/main.js"), /projectId: "central-do-casal"/);
});

test("Hosting workflow uses federated identity and does not deploy Firestore rules", () => {
  const workflow = read(".github/workflows/deploy-web-firebase.yml");
  assert.match(workflow, /GCP_WORKLOAD_IDENTITY_PROVIDER/);
  assert.match(workflow, /GCP_SERVICE_ACCOUNT/);
  assert.match(workflow, /google-github-actions\/auth@v2/);
  assert.match(workflow, /deploy --only hosting --project central-do-casal/);
  assert.match(workflow, /hosting:sites:list --project central-do-casal/);
});

test("Web entrypoint contains no embedded server private key", () => {
  const main = read("web/main.js");
  assert.doesNotMatch(main, /-----BEGIN (?:RSA |EC )?PRIVATE KEY-----/);
  assert.doesNotMatch(main, /["']private_key["']\s*:/);
});


test("Production deployment requires explicit confirmation on main and cannot trigger on push", () => {
  const workflow = read(".github/workflows/deploy-web-firebase.yml");
  assert.match(workflow, /workflow_dispatch:/);
  assert.match(workflow, /\$CONFIRM" != "PUBLICAR"/);
  assert.match(workflow, /\$GITHUB_REF" != "refs\/heads\/main"/);
  assert.doesNotMatch(workflow, /^  push:/m);
});
