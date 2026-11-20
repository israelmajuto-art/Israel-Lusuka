const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: denied across profiles, transactions, and opening balances", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("opening_balances").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      transactionId: "TX001",
      network: "MPESA",
      type: "MONEY_RECEIVED",
      amount: "25000.00",
      fee: "0.00",
      senderOrRecipientName: "JOHN DOE",
      transactionDateText: "20/11/2026",
      transactionTimeText: "10:00",
      timestampEpochMs: 1795168800000,
      description: "Received payment",
      verificationStatus: "FORMAT_VALIDATED",
      verificationNotes: "OK",
      manualEntry: false,
      creditAdjustment: true,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner: valid profile, transaction, and opening balance create/update/query succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const updated = new Date(Date.now() - 1000);

  // 1. User business profile create & update
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      businessName: "Biashara Yangu",
      primaryLanguage: "sw",
      currencyCode: "TZS",
      email: "owner@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    profileRef.update({
      businessName: "Duka la Mauzo",
      primaryLanguage: "en",
      updatedAt: updated,
    })
  );

  // 2. CloudTransactionRecord create & partial update (matches Kotlin serialization)
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("MPESA_TX12345");
  await assertSucceeds(
    txRef.set({
      userId: ALICE_UID,
      transactionId: "TX12345",
      network: "MPESA",
      type: "MONEY_RECEIVED",
      amount: "50000.00",
      fee: "0.00",
      senderOrRecipientName: "HALIMA SAID",
      senderOrRecipientPhone: "0754112233",
      transactionDateText: "20/11/2026",
      transactionTimeText: "11:00",
      timestampEpochMs: 1795172400000,
      description: "Malipo ya bidhaa",
      providerReportedBalance: "150000.00",
      verificationStatus: "FORMAT_VALIDATED",
      verificationNotes: "Umbizo limehakikiwa",
      manualEntry: false,
      creditAdjustment: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    txRef.update({
      senderOrRecipientName: "HALIMA SAID JUMA",
      description: "Malipo ya mchele",
      reconciliationNote: "Verified cashbook",
      updatedAt: updated,
    })
  );

  // List query matching Kotlin observeCloudTransactions
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions")
      .orderBy("timestampEpochMs", "desc")
      .get()
  );

  // 3. Opening balance create & update
  const obRef = aliceDb.collection("users").doc(ALICE_UID).collection("opening_balances").doc("MPESA");
  await assertSucceeds(
    obRef.set({
      userId: ALICE_UID,
      network: "MPESA",
      amount: "100000.00",
      effectiveDateEpochMs: 1795100000000,
      note: "Salio la asubuhi",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    obRef.update({
      amount: "120000.00",
      note: "Updated opening float",
      updatedAt: updated,
    })
  );
});

test("Cross-user isolation: Bob cannot read, list, update, or delete Alice's data", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const adminDb = context.firestore();
    const ts = new Date(Date.now() - 10000);
    await adminDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Alice Shop",
      primaryLanguage: "sw",
      currencyCode: "TZS",
      email: "alice@example.com",
      createdAt: ts,
      updatedAt: ts,
    });
    await adminDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").set({
      userId: ALICE_UID,
      transactionId: "TXA1",
      network: "AIRTEL_MONEY",
      type: "MONEY_RECEIVED",
      amount: "12000.00",
      fee: "0.00",
      senderOrRecipientName: "MUSA",
      transactionDateText: "20/11/2026",
      transactionTimeText: "09:15",
      timestampEpochMs: 1795166100000,
      description: "Payment",
      verificationStatus: "FORMAT_VALIDATED",
      verificationNotes: "OK",
      manualEntry: false,
      creditAdjustment: true,
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("transactions").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").update({
      description: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").delete()
  );
});

test("Red-team bounds: reject ghost fields, @DocumentId id key, future timestamps, and immutable field tampering", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Ghost field & redundant @DocumentId `id` field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_ghost").set({
      id: "tx_ghost",
      userId: ALICE_UID,
      transactionId: "TX99",
      network: "MPESA",
      type: "MONEY_RECEIVED",
      amount: "5000.00",
      fee: "0.00",
      senderOrRecipientName: "TEST",
      transactionDateText: "20/11/2026",
      transactionTimeText: "12:00",
      timestampEpochMs: 1795176000000,
      description: "Test",
      verificationStatus: "FORMAT_VALIDATED",
      verificationNotes: "OK",
      manualEntry: false,
      creditAdjustment: true,
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      businessName: "Shop",
      primaryLanguage: "sw",
      currencyCode: "TZS",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: created,
    })
  );

  // Valid create followed by immutable `createdAt` modification fails
  const txRef = aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_immut");
  await assertSucceeds(
    txRef.set({
      userId: ALICE_UID,
      transactionId: "TXIM1",
      network: "MIXX_BY_YAS",
      type: "MONEY_RECEIVED",
      amount: "7000.00",
      fee: "0.00",
      senderOrRecipientName: "ASHA",
      transactionDateText: "20/11/2026",
      transactionTimeText: "12:10",
      timestampEpochMs: 1795176600000,
      description: "Mixx payment",
      verificationStatus: "FORMAT_VALIDATED",
      verificationNotes: "OK",
      manualEntry: false,
      creditAdjustment: true,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    txRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});
