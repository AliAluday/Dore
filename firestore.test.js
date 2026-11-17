const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_nineveh_123";
const BOB_UID = "bob_nineveh_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validConfig(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    selectedDistrict: "كل مناطق نينوى",
    isDarkMode: true,
    userName: "علي حسين",
    userPhone: "07701234567",
    userAge: "",
    optionalNote: "",
    loyaltyPoints: 60,
    muteSound: false,
    muteVibration: false,
    allowNewBookings: true,
    enableFastestTurn: true,
    enableQrCode: true,
    enableWhatsAppShare: true,
    enableReviews: true,
    enableLoyalty: true,
    enableOptionalFields: true,
    enableLiveAlerts: true,
    pointsPerBooking: 10,
    disabledCategories: [],
    broadcastMessage: "",
    createdAt: now,
    updatedAt: now,
  };
}

function validVenue(uid, venueId = 1) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    venueId,
    name: "عيادة الحدباء للأسنان",
    city: "الموصل - الأيسر",
    category: "عيادات",
    specialty: "طب الأسنان",
    area: "حي الزهور",
    priceDinar: 20000,
    rating: 4.9,
    reviewCount: 3,
    workingHours: "09:00 ص - 10:00 م",
    phone: "+9647701112233",
    whatsapp: "+9647701112233",
    latitude: 36.3642,
    longitude: 43.1755,
    description: "بإشراف د. سرمد العبيدي",
    customOptionsCsv: "فحص وتشخيص|تنظيف وتبييض",
    open: true,
    paused: false,
    acceptingBookings: true,
    currentServingNumber: 1,
    nextTicketNumber: 4,
    servedTodayCount: 2,
    avgMinutesPerPerson: 6,
    favorite: false,
    waitingCount: 2,
    createdAt: now,
    updatedAt: now,
  };
}

function validBooking(uid, bookingId = 1) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    bookingId,
    ticketCode: "NIN-1-1",
    queueNumber: 1,
    venueId: 1,
    venueName: "عيادة الحدباء للأسنان",
    venueCity: "الموصل - الأيسر • حي الزهور",
    venueCategory: "عيادات",
    venuePhone: "+9647701112233",
    customerName: "علي حسين الجبوري",
    customerPhone: "07701234567",
    selectedOption: "فحص وتشخيص",
    notes: "",
    optionalInfo: "متابعة دورية",
    priceDinar: 20000,
    status: "ACTIVE",
    stage: "MONITORED",
    peopleAhead: 1,
    estimatedMinutes: 6,
    createdAtMillis: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
  };
}

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

test("Unauthenticated users are completely blocked from reading or writing Dori sync collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/venues`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/config`).doc("settings").set(validConfig(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/bookings`).doc("booking_1").set(validBooking(ALICE_UID))
  );
});

test("Owner can create, list, and partially update config, venues, bookings, queue items, and reviews", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Config create & partial update
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/config`).doc("settings").set(validConfig(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/config`).doc("settings").update({
      allowNewBookings: false,
      broadcastMessage: "صيانة سريعة من لوحة التحكم",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Venue create & Control update (must preserve createdAt)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/venues`).doc("venue_1").set(validVenue(ALICE_UID, 1))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/venues`).doc("venue_1").update({
      open: false,
      priceDinar: 25000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Booking create, active transition -> COMPLETED, and immutable terminal status lock
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bookings`).doc("booking_1").set(validBooking(ALICE_UID, 1))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bookings`).doc("booking_1").update({
      status: "COMPLETED",
      stage: "YOUR_TURN",
      peopleAhead: 0,
      estimatedMinutes: 0,
      completedAtMillis: Date.now() - 500,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/bookings`).doc("booking_1").update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // List subcollections (owner)
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/venues`).get());
  await assertSucceeds(aliceDb.collection(`users/${ALICE_UID}/bookings`).get());
});

test("Cross-user isolation, ghost fields, future timestamps, and root /users/{uid} access are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/config`).doc("settings").set(validConfig(ALICE_UID))
  );

  // Bob cannot read/write Alice's data
  await assertFails(bobDb.collection(`users/${ALICE_UID}/config`).doc("settings").get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/config`).doc("settings").update({
      allowNewBookings: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/venues`).get());

  // Root /users/{uid} must remain unreadable/unwritable
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set({ hello: "world" }));

  // Ghost field & future timestamp rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/venues`).doc("venue_bad").set({
      ...validVenue(ALICE_UID, 99),
      isAdmin: true,
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/bookings`).doc("booking_future").set({
      ...validBooking(ALICE_UID, 2),
      createdAt: new Date(Date.now() + 60000),
    })
  );
});
