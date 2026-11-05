# livemcq-server

Live MCQ-এর ব্যাকএন্ড: Java 21 + Spring Boot 3.5 + PostgreSQL। ধাপ ১ সম্পন্ন: প্রজেক্ট কাঠামো, ডেটাবেস স্কিমা, এন্টিটি ও রিপজিটরি।

## চালানোর নিয়ম

```bash
# ১. ডেটাবেস (একবারই)
createdb livemcq
createdb livemcq_test        # শুধু টেস্টের জন্য, খালি থাকতে হবে

# ২. স্কিমার টেস্ট: আলাদা অস্থায়ী PostgreSQL চালিয়ে ৪৮টি চেক (কিছুই ইনস্টল/বদল করে না)
./db-tests/run-db-tests.sh

# ২ক. প্রোটোটাইপ-মিল টেস্ট: HTML-এর নিজের কোড চালিয়ে ফল বের করে ডেটাবেসের ভিউয়ের সাথে সংখ্যায় সংখ্যায় মেলায়
#     (node ও python3 লাগে; HTML বদলালে db-tests/parity/prototype.html আপডেট করুন)
./db-tests/parity/run-parity.sh

# ৩. Java টেস্ট (ExamTest ডেটাবেস ছাড়া, SchemaIntegrationTest আসল PostgreSQL-এ)
LIVEMCQ_TEST_DB_URL=jdbc:postgresql://localhost:5432/livemcq_test \
LIVEMCQ_TEST_DB_PASSWORD=<আপনার পাসওয়ার্ড> mvn test

# ৪. সার্ভার চালু (ধাপ ২-এ API যোগ হলে কাজে আসবে)
LIVEMCQ_DB_PASSWORD=<আপনার পাসওয়ার্ড> mvn spring-boot:run
```

## ফোল্ডার

| পথ | কী আছে |
|---|---|
| `src/main/resources/db/migration/` | `V1` সব টেবিল, কনস্ট্রেইন্ট ও স্কোর/র‍্যাংকের ভিউ; `V2` সামগ্রিক (শ্রেণিভিত্তিক) লিডারবোর্ড। ডেটাবেস বদলাতে হলে নতুন `V2__...sql` ফাইল, পুরোনোটা কখনো এডিট নয় |
| `domain/` | JPA এন্টিটি (`Exam`, `Question`, `Attempt` ...) |
| `repo/` | Spring Data রিপজিটরি |
| `db-tests/` | স্কিমার SQL টেস্ট (`schema_checks.sql`) ও HTML-বনাম-ডেটাবেস মিল টেস্ট (`parity/`) |
| `src/test/` | JUnit টেস্ট |

## যে সিদ্ধান্তগুলো HTML প্রোটোটাইপ থেকে আলাদা

1. **টেন্যান্ট:** `institutions` টেবিল এবং প্রতিটি টেবিলে `institution_id`। কম্পোজিট FK দিয়ে এক প্রতিষ্ঠানের ডেটা অন্যটির সাথে জোড়া লাগানো অসম্ভব।
2. **নম্বর সেভ হয় না।** HTML-এ `attempt.marks` সেভ হতো। এখানে নম্বর, সঠিক/ভুল/বাকি ও র‍্যাংক `attempt_scores`/`attempt_ranks` ভিউ থেকে উত্তর দেখে হিসাব হয়, তাই বোনাস বদলালে কোনো ডেটা পুরোনো থাকে না।
3. **পূর্ণমান = প্রশ্ন × নম্বর + বোনাস** (ভিউ `exam_totals` এবং `Exam.fullMarks()`)।
4. **প্রকাশ:** `published` + `pubSeq`-এর বদলে একটাই `published_at` (NULL = অপ্রকাশিত)।
5. **উত্তর:** প্রতিটি প্রশ্নের উত্তর আলাদা সারিতে (`attempt_answers`), উত্তর না দিলে সারি নেই বা `NULL`, দুটোই "বাকি" গোনা হয়।
6. **র‍্যাংক:** `RANK()` উইন্ডো ফাংশন (১, ২, ২, ৪), HTML-এর নিয়মের হুবহু মিল।
7. **শিক্ষার্থীর শ্রেণি:** আলাদা `classId` নয়, শাখা (`section_id`) থেকেই শ্রেণি পাওয়া যায়।
8. **পাসওয়ার্ড:** `password_hash` কলাম (BCrypt, ধাপ ২-এ যোগ হবে), plain text নয়।

## পরের ধাপ

ধাপ ২: লগইন ও রোল (Spring Security + JWT)। ধাপ ৩: পরীক্ষা তৈরি ও জমা, ধাপ ৪: ফলাফল, লিডারবোর্ড ও বোনাস API।

---

## ধাপ ২ — লগইন ও রোল (Spring Security + JWT)

**এন্ডপয়েন্ট**
| | |
|---|---|
| `POST /api/auth/login` | body: `{"institution":"demo","roll":"admin","password":"admin"}` → `{token, tokenType, expiresInSeconds, user}` |
| `GET /api/auth/me` | হেডার `Authorization: Bearer <token>` → বর্তমান ইউজার (ক্লাস/সেকশনসহ) |
| `/api/admin/**` | শুধু ADMIN ও TEACHER |
| `/api/student/**` | শুধু STUDENT |

সব এরর একই আকারে: `{timestamp, status, code, message, fieldErrors?}`।

**টেস্ট চালান** (ধাপ ১-এর মতোই, `livemcq_test` ডেটাবেস লাগবে):
```powershell
$env:LIVEMCQ_TEST_DB_PASSWORD = "আপনার-পাসওয়ার্ড"
mvn test
```

**হাতে চালিয়ে দেখুন (ঐচ্ছিক)** — একটি আলাদা ডেটাবেস `livemcq` বানিয়ে:
```powershell
psql -U postgres -c "CREATE DATABASE livemcq;"
$env:LIVEMCQ_DB_PASSWORD = "আপনার-পাসওয়ার্ড"
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```
`dev` প্রোফাইল ডেমো ডেটা বানায় (institution `demo`; `admin/admin`, `teacher/teacher`, `1001/1234`)।
তারপর অন্য টার্মিনালে:
```powershell
curl.exe -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d "{\"institution\":\"demo\",\"roll\":\"admin\",\"password\":\"admin\"}"
```

**প্রোডাকশনে** `LIVEMCQ_JWT_SECRET` (৩২+ অক্ষর) না দিলে অ্যাপ চালুই হবে না — ইচ্ছাকৃত।

**HTML থেকে পার্থক্য (ধাপ ২)**
- লগইনে `institution` লাগে (মাল্টি-টেন্যান্ট)।
- পাসওয়ার্ড BCrypt হ্যাশ; টোকেন ৮ ঘণ্টা বৈধ।
- "রোল ২-৮ সংখ্যা / পাসওয়ার্ড ≥৪ অক্ষর" নিয়ম ইউজার *তৈরির* সময় খাটে; ইউজার-তৈরির API আসবে শিক্ষার্থী-ব্যবস্থাপনার ধাপে, তাই নিয়মটা তখন যুক্ত হবে।

---

## ধাপ ৩ক — ক্লাস/সেকশন, নিবন্ধন, শিক্ষার্থী, পরীক্ষা

| এন্ডপয়েন্ট | কে | কাজ |
|---|---|---|
| `POST /api/auth/register` | সবাই | শিক্ষার্থী নিবন্ধন (`institution,name,roll,password,sectionId`); রোল ২–৮ সংখ্যা (বাংলা অঙ্কও চলে), পাসওয়ার্ড ≥৪; সাথে সাথে লগইন |
| `GET /api/public/institutions/{slug}/classes` | সবাই | নিবন্ধন ফর্মের জন্য ক্লাস ও সেকশন |
| `GET/POST /api/admin/classes` | ADMIN/TEACHER | ক্লাস তালিকা (গণনাসহ) / নতুন ক্লাস (ডিফল্ট শাখা "ক") |
| `POST /api/admin/classes/{id}/sections` | ADMIN/TEACHER | নতুন শাখা |
| `GET /api/admin/students` | ADMIN/TEACHER | শিক্ষার্থী তালিকা |
| `PATCH /api/admin/students/{id}/enrollment` | ADMIN/TEACHER | শাখা বদল (`{"sectionId":..}`) |
| `POST/GET /api/admin/exams`, `GET/DELETE /api/admin/exams/{id}` | ADMIN/TEACHER | পরীক্ষা তৈরি/তালিকা/বিস্তারিত/মোছা |
| `PUT /api/admin/exams/{id}/questions` | ADMIN/TEACHER | প্রশ্ন বদল — প্রকাশের পর বা কেউ জমা দিলে ৪০৯ |

**HTML থেকে পার্থক্য (৩ক)**
- নিবন্ধনে ক্লাস+শাখার নাম নয়, `sectionId` যায়; নিবন্ধনেও `institution` লাগে।
- অ্যাডমিন রুটগুলো `/api/admin/...`-এর নিচে, শিক্ষকও ব্যবহার করতে পারে।
- প্রশ্ন বদলানো প্রোটোটাইপে ছিল না; এখানে যোগ হয়েছে, তবে কেউ জমা দিলে বন্ধ (নইলে জমা-দেওয়া উত্তর নষ্ট হতো)।
- নম্বর কখনো সংরক্ষিত নয়; পূর্ণমান = প্রশ্ন×নম্বর + বোনাস সব সময় হিসাব করে আসে।
- সব রুটে প্রতিষ্ঠান-পরীক্ষা: অন্য প্রতিষ্ঠানের আইডি দিলে "পাওয়া যায়নি" (৪০৪)।

---

## ধাপ ৩খ — পরীক্ষা দেওয়া (সার্ভার-টাইমার)

| এন্ডপয়েন্ট (শুধু STUDENT) | কাজ |
|---|---|
| `GET /api/student/exams` | নিজের ক্লাসের পরীক্ষা ও অবস্থা: `AVAILABLE / IN_PROGRESS / SUBMITTED / EXPIRED / MISSED` |
| `POST /api/student/exams/{id}/start` | **সার্ভার** সময় শুরু করে; প্রশ্নপত্র (সঠিক উত্তর ছাড়া) ও `remainingSeconds` দেয়। আবার চাপলে বাকি সময় নিয়ে ফেরত (reload/অ্যাপ বন্ধ হলেও ঘড়ি থামে না) |
| `POST /api/student/exams/{id}/submit` | `{"answers":[0,null,3]}` — প্রতি প্রশ্নে ১টা (ফাঁকা = `null`)। সময় সার্ভার মাপে |

**নিয়ম:** শুরু না করে জমা ৪০৯ `NOT_STARTED`; সময়সীমা + ১৫ সেকেন্ড পরে ৪০৯ `TIME_EXPIRED`; একবারের বেশি জমা ৪০৯ `ALREADY_SUBMITTED`; প্রকাশিত পরীক্ষা ৪০৯ `EXAM_CLOSED`; অন্য ক্লাসের পরীক্ষা ৪০৩, অন্য প্রতিষ্ঠানের ৪০৪। কেউ শুরু করলে অ্যাডমিন আর প্রশ্ন বদলাতে পারে না (`EXAM_STARTED`)।

**HTML থেকে পার্থক্য (৩খ)**
- HTML-এ ক্লায়েন্ট নিজের `timeSec` পাঠাত; এখানে সার্ভার `জমার সময় − শুরুর সময়` মাপে (সর্বনিম্ন ১ সেকেন্ড, সর্বোচ্চ পরীক্ষার সময়)। ফলে টাইমার ফাঁকি দেওয়া যায় না।
- HTML-এ "পেপার খোলা" আর "শুরু" একই ছিল; এখানে `start` ঘড়ি চালু করে এবং নতুন টেবিল `exam_sessions`-এ রাখে।
- সময় শেষে নিজে নিজে জমা (auto-submit) এখনো নেই: ক্লায়েন্ট ০ হলে জমা দেবে। না দিলে `EXPIRED` হয়ে যায়। সার্ভার-সাইড auto-submit পরের ধাপে ভাবা যাবে।
- জমার জবাবে নম্বর আসে না; নম্বর ও র‍্যাংক ফলাফল প্রকাশের পর (ধাপ ৪)।

---

## ধাপ ৪ — ফলাফল, র‍্যাংক, বোনাস, লিডারবোর্ড

**অ্যাডমিন/শিক্ষক (`/api/admin/...`)**
| | |
|---|---|
| `POST/DELETE /exams/{id}/publish` | ফলাফল প্রকাশ / প্রত্যাহার (কেউ জমা না দিলে ৪২২ `NO_ATTEMPTS`) |
| `POST /exams/{id}/bonus` | `{"bonus": 0..100}` — সবাইকে সমান বোনাস; নতুন বোনাস পুরনোটা **বদলায়** (যোগ হয় না), ০ দিলে সরে যায়; পূর্ণমানও একই পরিমাণ বাড়ে |
| `GET /exams/{id}/leaderboard` | র‍্যাংকসহ তালিকা (সঠিক/ভুল/ফাঁকা, সময়) |
| `GET /exams/{id}/attempts/{studentId}` | শিক্ষার্থীর উত্তরপত্র: নম্বর, বোনাস, র‍্যাংক, প্রশ্নভিত্তিক রিভিউ |
| `GET /summary` | ড্যাশবোর্ড: শিক্ষার্থী/পরীক্ষা সংখ্যা, অপেক্ষমাণ, প্রকাশিত |
| `GET /classes/{c}/sections/{s}/students` | শাখার তালিকা (কয়টা দিয়েছে, মোট নম্বর) |
| `GET /students/{id}/report` | শিক্ষার্থীর রিপোর্ট |

**শিক্ষার্থী (`/api/student/...`), সবই প্রকাশের পরে**
`GET /exams/{id}/result`, `GET /exams/{id}/leaderboard`, `GET /leaderboard/overall`।

**মূল নিয়ম:** নম্বর কোথাও সংরক্ষিত নয়; `attempt_ranks` / `overall_ranks` ভিউ থেকে আসে (HTML-এর সাথে ২৪৯টা তুলনায় মেলানো)। তাই বোনাস বদলালে সব নম্বর-র‍্যাংক-পূর্ণমান একসাথে ঠিক থাকে।

**HTML থেকে পার্থক্য (ধাপ ৪)**
- `published` + `pubSeq`-এর বদলে `published_at` সময়; "সর্বশেষ প্রকাশিত আগে" এখান থেকেই।
- একই র‍্যাংকের শিক্ষার্থী (নম্বর ও সময় সমান) এখানে রোল-আইডি ধরে স্থির ক্রমে আসে।
- শিক্ষার্থীর ফলাফল/লিডারবোর্ডে অন্যদের `userId` যায় না।
- API-র পথ HTML-এর `/api/exams/..` নয়, `/api/admin/..` ও `/api/student/..` ভাগ করা।

---

## OpenAPI চুক্তি (Next.js ও Flutter-এর জন্য)

সার্ভার কন্ট্রোলার দেখে নিজেই API-র বিবরণ বানায় (springdoc) — হাতে লেখা নয়, তাই কোডের সাথে অমিল হয় না।

- `mvn test` চালালে **`docs/openapi.json`** নতুন করে লেখা হয় (`OpenApiContractTest`)। এই ফাইলই ক্লায়েন্ট বানানোর উৎস।
- ২৮টা অপারেশনের তালিকা টেস্টে বাঁধা; কোনো রুট হারালে বা বাড়লে (ইচ্ছা করে তালিকায় না যোগ করলে) টেস্ট ফেল করে।
- অপারেশনের নাম স্থির: `ExamAdminController.create` → `examAdminCreate`।
- সব এরর একই আকার `ApiError` (`code`, `message`, `fieldErrors`)।
- হাতে দেখতে: dev প্রোফাইলে অ্যাপ চালিয়ে http://localhost:8080/swagger-ui.html খুলুন; `/api/auth/login` চালিয়ে টোকেন নিন, উপরে **Authorize**-এ বসান।
- প্রোডাকশনে এটা **বন্ধ** (`LIVEMCQ_API_DOCS=true` দিলে চালু হয়)।
