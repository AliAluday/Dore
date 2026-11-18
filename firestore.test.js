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
    controlRevision: 0,
    queueResetRevision: 0,
    loyaltyRevision: 0,
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
    approvalStatus: "PENDING",
    handledByProvider: "",
    approvalNote: "",
    peopleAhead: 1,
    estimatedMinutes: 6,
    createdAtMillis: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
  };
}

function validSupportTicket(uid, externalId = "SUP-ABCD1234") {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    externalId,
    senderName: "علي حسين",
    senderPhone: "07701234567",
    category: "GENERAL",
    requestType: "CHAT",
    relatedVenueName: "",
    subject: "استفسار عام",
    message: "أحتاج المساعدة في حجز دور",
    status: "SENT",
    adminReply: "",
    repliedByAdmin: "",
    createdAtMillis: Date.now() - 2000,
    updatedAtMillis: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
  };
}

function validSupportMessage(uid, messageId = "MSG-1234567890", ticketExternalId = "SUP-ABCD1234", fromAdmin = false) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    externalMessageId: messageId,
    ticketExternalId,
    fromAdmin,
    senderName: fromAdmin ? "فريق الدعم الفني" : "علي حسين",
    body: fromAdmin ? "أهلاً بك، تم استلام طلبك" : "أحتاج المساعدة في حجز دور",
    createdAtMillis: Date.now() - 1500,
    createdAt: now,
    updatedAt: now,
  };
}

function validAdmin(uid, adminId = 1) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    adminId,
    name: "أحمد الموصلي",
    roleTitle: "مدير النظام العام",
    phone: "07701002000",
    active: true,
    superAdmin: true,
    canManageFeatures: true,
    canManageVenues: true,
    canManageQueues: true,
    canReplySupport: true,
    canManageProviders: true,
    canManageAdmins: true,
    canRunCommands: true,
    createdAtMillis: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
  };
}

function validProvider(uid, providerId = 1) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    providerId,
    name: "د. سرمد العبيدي",
    providerRole: "DOCTOR",
    customTitle: "طبيب أسنان",
    phone: "+9647701112233",
    venueId: 1,
    venueName: "عيادة الحدباء التخصصية للأسنان",
    canAcceptTickets: true,
    canCallNext: true,
    canPauseVenue: true,
    active: true,
    acceptedCount: 12,
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

test("Unauthenticated users are blocked from all collections including support, admins, providers", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/venues`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/support_tickets`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/support_messages`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/admins`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/providers`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/config`).doc("settings").set(validConfig(ALICE_UID))
  );
});

test("Owner can manage config, venues, bookings (including approval), support threads, admins, and providers", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/config`).doc("settings").set(validConfig(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/config`).doc("settings").update({
      allowNewBookings: false,
      controlRevision: 10,
      broadcastMessage: "صيانة سريعة",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/venues`).doc("venue_1").set(validVenue(ALICE_UID, 1))
  );

  // Booking create, approval update, and completion transition
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bookings`).doc("booking_NIN-1-1").set(validBooking(ALICE_UID, 1))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bookings`).doc("booking_NIN-1-1").update({
      approvalStatus: "ACCEPTED",
      handledByProvider: "د. سرمد العبيدي",
      approvalNote: "تم تأكيد قبول التذكرة",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/bookings`).doc("booking_NIN-1-1").update({
      status: "COMPLETED",
      stage: "YOUR_TURN",
      peopleAhead: 0,
      estimatedMinutes: 0,
      completedAtMillis: Date.now() - 500,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/bookings`).doc("booking_NIN-1-1").update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Support ticket + chat messages (messages are immutable on update)
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/support_tickets`).doc("SUP-ABCD1234").set(validSupportTicket(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/support_tickets`).doc("SUP-ABCD1234").update({
      status: "REPLIED",
      adminReply: "تم الحل",
      repliedByAdmin: "أحمد الموصلي",
      updatedAtMillis: Date.now() - 400,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/support_messages`).doc("MSG-1234567890").set(validSupportMessage(ALICE_UID))
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/support_messages`).doc("MSG-1234567890").update({
      body: "tampered",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Admins & Providers
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/admins`).doc("admin_1").set(validAdmin(ALICE_UID, 1))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/admins`).doc("admin_1").update({
      canReplySupport: false,
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/providers`).doc("provider_1").set(validProvider(ALICE_UID, 1))
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/providers`).doc("provider_1").update({
      acceptedCount: 13,
      updatedAt: new Date(Date.now() - 400),
    })
  );
});

test("Cross-user isolation, ID mismatches, ghost fields, and invalid enums are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/support_tickets`).doc("SUP-ABCD1234").set(validSupportTicket(ALICE_UID))
  );
  await assertFails(bobDb.collection(`users/${ALICE_UID}/support_tickets`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/admins`).get());
  await assertFails(
    bobDb.collection(`users/${ALICE_UID}/providers`).doc("provider_1").set(validProvider(ALICE_UID, 1))
  );

  // Doc ID mismatch for support ticket or message rejected
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/support_tickets`).doc("WRONG-ID").set(validSupportTicket(ALICE_UID, "SUP-ABCD1234"))
  );
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/support_messages`).doc("WRONG-MSG").set(validSupportMessage(ALICE_UID, "MSG-1234567890"))
  );

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
