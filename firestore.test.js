const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_bootstrap_001";
const ADMIN_EMAIL = "alialhadidia2008@gmail.com";
const SECOND_ADMIN_UID = "admin_staff_002";
const USER_A_UID = "customer_alice_123";
const USER_B_UID = "customer_bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function bootstrapAdminDb() {
  return testEnv
    .authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();
}

function unverifiedAdminDb() {
  return testEnv
    .authenticatedContext("spoof_uid_999", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();
}

function validPlatformConfig() {
  const now = new Date(Date.now() - 1000);
  return {
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
    controlRevision: 1,
    queueResetRevision: 0,
    createdAt: now,
    updatedAt: now,
  };
}

function validUserProfile(uid, email = "user@example.com") {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    email,
    selectedDistrict: "كل مناطق نينوى",
    isDarkMode: true,
    userName: "علي حسين",
    userPhone: "07701234567",
    userAge: "",
    optionalNote: "",
    loyaltyPoints: 0,
    muteSound: false,
    muteVibration: false,
    favoriteVenueIds: [],
    createdAt: now,
    updatedAt: now,
  };
}

function validVenue(venueId = 1) {
  const now = new Date(Date.now() - 1000);
  return {
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
    currentServingNumber: 0,
    nextTicketNumber: 1,
    servedTodayCount: 0,
    avgMinutesPerPerson: 6,
    waitingCount: 0,
    createdAt: now,
    updatedAt: now,
  };
}

function validBooking(uid, bookingId = 1, ticketCode = "NIN-1-1", queueNumber = 1) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    bookingId,
    ticketCode,
    queueNumber,
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
    stage: "YOUR_TURN",
    approvalStatus: "PENDING",
    handledByProvider: "",
    approvalNote: "",
    peopleAhead: 0,
    estimatedMinutes: 0,
    createdAtMillis: Date.now() - 2000,
    createdAt: now,
    updatedAt: now,
  };
}

function validQueueItem(uid, queueItemId = 1, ticketCode = "NIN-1-1", ticketNumber = 1) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    queueItemId,
    venueId: 1,
    ticketNumber,
    ticketCode,
    customerName: "علي حسين الجبوري",
    customerPhone: "07701234567",
    selectedOption: "فحص وتشخيص",
    status: "SERVING",
    bookingId: 1,
    bookingTicketCode: ticketCode,
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

function validSupportMessage(ownerUid, messageId, ticketExternalId, fromAdmin) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: ownerUid,
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

function validAdminProfile(adminId = 1) {
  const now = new Date(Date.now() - 1000);
  return {
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

function validProvider(providerId = 1) {
  const now = new Date(Date.now() - 1000);
  return {
    providerId,
    name: "د. سرمد العبيدي",
    providerRole: "DOCTOR",
    customTitle: "طبيب أسنان",
    phone: "+9647701112233",
    venueId: 1,
    venueName: "عيادة الحدباء للأسنان",
    canAcceptTickets: true,
    canCallNext: true,
    canPauseVenue: true,
    active: true,
    acceptedCount: 0,
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

test("Unauthenticated users are blocked from all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("platform").doc("settings").get());
  await assertFails(unauthDb.collection("venues").get());
  await assertFails(unauthDb.collection("bookings").get());
  await assertFails(unauthDb.collection("queue_items").get());
  await assertFails(unauthDb.collection("support_tickets").get());
  await assertFails(unauthDb.collection("support_messages").get());
  await assertFails(unauthDb.collection("admins").get());
  await assertFails(unauthDb.collection("providers").get());
  await assertFails(unauthDb.collection("admin_users").doc(ADMIN_UID).get());
});

test("Unverified spoofed admin email is strictly rejected from admin actions", async () => {
  const spoofDb = unverifiedAdminDb();
  await assertFails(
    spoofDb.collection("platform").doc("settings").set(validPlatformConfig())
  );
  await assertFails(
    spoofDb.collection("venues").doc("venue_1").set(validVenue(1))
  );
  await assertFails(spoofDb.collection("bookings").get());
  await assertFails(spoofDb.collection("admins").get());
});

test("Multi-user interconnected flow: Admin manages catalog & config; User A books; Admin accepts; User B is isolated", async () => {
  const adminDb = bootstrapAdminDb();
  const userADb = testEnv.authenticatedContext(USER_A_UID).firestore();
  const userBDb = testEnv.authenticatedContext(USER_B_UID).firestore();

  // 1. Admin creates global platform settings and venues
  await assertSucceeds(
    adminDb.collection("platform").doc("settings").set(validPlatformConfig())
  );
  await assertSucceeds(
    adminDb.collection("venues").doc("venue_1").set(validVenue(1))
  );

  // Regular user can read platform/settings & venues, but cannot write platform/settings or edit venue prices
  await assertSucceeds(userADb.collection("platform").doc("settings").get());
  await assertSucceeds(userADb.collection("venues").get());
  await assertFails(
    userADb.collection("platform").doc("settings").update({
      allowNewBookings: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    userADb.collection("venues").doc("venue_1").update({
      priceDinar: 1000,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 2. User A creates private profile (must start at 0 loyaltyPoints)
  await assertFails(
    userADb.collection("users").doc(USER_A_UID).set({
      ...validUserProfile(USER_A_UID),
      loyaltyPoints: 500,
    })
  );
  await assertSucceeds(
    userADb.collection("users").doc(USER_A_UID).set(validUserProfile(USER_A_UID))
  );
  // User B cannot read User A's profile
  await assertFails(userBDb.collection("users").doc(USER_A_UID).get());
  // User A can save personal favoriteVenueIds
  await assertSucceeds(
    userADb.collection("users").doc(USER_A_UID).update({
      favoriteVenueIds: [1],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 3. User A books a ticket + increments venue queue counter atomically
  await assertFails(
    userADb.collection("bookings").doc("booking_forged").set({
      ...validBooking(USER_A_UID, 1, "NIN-1-1", 1),
      approvalStatus: "ACCEPTED",
    })
  );
  await assertSucceeds(
    userADb.collection("bookings").doc("booking_NIN-1-1").set(validBooking(USER_A_UID, 1, "NIN-1-1", 1))
  );
  await assertSucceeds(
    userADb.collection("queue_items").doc("queue_NIN-1-1").set(validQueueItem(USER_A_UID, 1, "NIN-1-1", 1))
  );
  await assertSucceeds(
    userADb.collection("venues").doc("venue_1").update({
      currentServingNumber: 1,
      nextTicketNumber: 2,
      waitingCount: 0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 4. User A can query own bookings; User B CANNOT read/list User A's bookings or queue_items
  await assertSucceeds(
    userADb.collection("bookings").where("userId", "==", USER_A_UID).get()
  );
  await assertFails(userADb.collection("bookings").get()); // unfiltered list denied for non-admin
  await assertFails(userBDb.collection("bookings").doc("booking_NIN-1-1").get());
  await assertFails(
    userBDb.collection("bookings").where("userId", "==", USER_A_UID).get()
  );
  await assertFails(userBDb.collection("queue_items").doc("queue_NIN-1-1").get());

  // User A cannot self-approve their own booking
  await assertFails(
    userADb.collection("bookings").doc("booking_NIN-1-1").update({
      approvalStatus: "ACCEPTED",
      handledByProvider: "Self",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // 5. Control App Admin lists ALL bookings across users & accepts User A's ticket!
  await assertSucceeds(adminDb.collection("bookings").get());
  await assertSucceeds(adminDb.collection("queue_items").get());
  await assertSucceeds(
    adminDb.collection("bookings").doc("booking_NIN-1-1").update({
      approvalStatus: "ACCEPTED",
      handledByProvider: "د. سرمد العبيدي",
      approvalNote: "تم قبول الحجز",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // 6. User A completes their booking & earns bounded loyalty points + submits public review
  await assertSucceeds(
    userADb.collection("bookings").doc("booking_NIN-1-1").update({
      status: "COMPLETED",
      stage: "YOUR_TURN",
      peopleAhead: 0,
      estimatedMinutes: 0,
      completedAtMillis: Date.now() - 300,
      userRating: 5,
      userReview: "خدمة سريعة وممتازة",
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertSucceeds(
    userADb.collection("users").doc(USER_A_UID).update({
      loyaltyPoints: 10,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  // Cannot jump loyaltyPoints by +1000
  await assertFails(
    userADb.collection("users").doc(USER_A_UID).update({
      loyaltyPoints: 1010,
      updatedAt: new Date(Date.now() - 250),
    })
  );
});

test("Multi-user two-way Support Chat: User A sends, Admin replies with User A's userId, User A sees reply, User B isolated", async () => {
  const adminDb = bootstrapAdminDb();
  const userADb = testEnv.authenticatedContext(USER_A_UID).firestore();
  const userBDb = testEnv.authenticatedContext(USER_B_UID).firestore();

  // User A opens chat ticket & sends initial message
  await assertSucceeds(
    userADb.collection("support_tickets").doc("SUP-ABCD1234").set(validSupportTicket(USER_A_UID))
  );
  await assertSucceeds(
    userADb
      .collection("support_messages")
      .doc("MSG-U-001")
      .set(validSupportMessage(USER_A_UID, "MSG-U-001", "SUP-ABCD1234", false))
  );

  // User A cannot forge an admin message
  await assertFails(
    userADb
      .collection("support_messages")
      .doc("MSG-FAKE-ADMIN")
      .set(validSupportMessage(USER_A_UID, "MSG-FAKE-ADMIN", "SUP-ABCD1234", true))
  );

  // Checking non-existent message doc safely returns exists=false without PERMISSION_DENIED
  const nonExistentSnap = await assertSucceeds(
    userADb.collection("support_messages").doc("MSG-NOT-YET-CREATED").get()
  );
  if (nonExistentSnap.exists) {
    throw new Error("Expected non-existent support message to have exists=false");
  }

  // User B cannot read User A's existing ticket or messages
  await assertFails(userBDb.collection("support_tickets").doc("SUP-ABCD1234").get());
  await assertFails(userBDb.collection("support_messages").doc("MSG-U-001").get());
  await assertFails(
    userBDb.collection("support_messages").where("userId", "==", USER_A_UID).get()
  );

  // Admin reads all support tickets & replies preserving userId = USER_A_UID
  await assertSucceeds(adminDb.collection("support_tickets").get());
  await assertSucceeds(adminDb.collection("support_messages").get());
  await assertSucceeds(
    adminDb
      .collection("support_messages")
      .doc("MSG-A-002")
      .set(validSupportMessage(USER_A_UID, "MSG-A-002", "SUP-ABCD1234", true))
  );
  await assertSucceeds(
    adminDb.collection("support_tickets").doc("SUP-ABCD1234").update({
      status: "REPLIED",
      adminReply: "أهلاً بك، تم استلام طلبك",
      repliedByAdmin: "أحمد الموصلي",
      updatedAtMillis: Date.now() - 400,
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // User A queries own support_messages & gets admin's reply!
  const userAMsgs = await assertSucceeds(
    userADb.collection("support_messages").where("userId", "==", USER_A_UID).get()
  );
  if (userAMsgs.size !== 2) {
    throw new Error(`Expected 2 messages for User A, got ${userAMsgs.size}`);
  }
});

test("Database-backed RBAC: Bootstrapped admin authorizes a second Google admin account via admin_users", async () => {
  const adminDb = bootstrapAdminDb();
  const staffDb = testEnv.authenticatedContext(SECOND_ADMIN_UID).firestore();
  const now = new Date(Date.now() - 1000);

  // Before authorization, staffDb cannot access Control App collections
  await assertFails(staffDb.collection("admins").get());
  await assertFails(staffDb.collection("providers").get());

  // Bootstrapped admin authorizes SECOND_ADMIN_UID
  await assertSucceeds(
    adminDb.collection("admin_users").doc(SECOND_ADMIN_UID).set({
      uid: SECOND_ADMIN_UID,
      email: "staff@dori-nineveh.iq",
      displayName: "سارة مشرفة التشغيل",
      role: "ADMIN",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Now staffDb can read its own admin_users doc and manage Control App collections!
  await assertSucceeds(staffDb.collection("admin_users").doc(SECOND_ADMIN_UID).get());
  await assertSucceeds(staffDb.collection("admins").doc("admin_1").set(validAdminProfile(1)));
  await assertSucceeds(staffDb.collection("providers").doc("provider_1").set(validProvider(1)));
  await assertSucceeds(staffDb.collection("bookings").get());
});
