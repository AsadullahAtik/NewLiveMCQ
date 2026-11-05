// Runs the PROTOTYPE's own logic (the HTML file) on its demo data and writes two files:
//   fixture.sql   - the same data as INSERTs for the new PostgreSQL schema
//   expected.json - what the prototype computed (marks, ranks, full marks, overall leaderboard)
// compare.py then checks that the database views produce exactly the same numbers.
//
// usage: node export-prototype.js <prototype.html> <out-dir>
const fs = require('fs');
const path = require('path');
const [, , htmlPath, outDir] = process.argv;
const html = fs.readFileSync(htmlPath, 'utf8');
const js = html.split('<script>')[1].split('</script>')[0];
const mod = { exports: {} };
new Function('module', js)(mod);
const L = mod.exports;

const d = L.seed();
L.setDb(d);
L.setSession(null);
const call = (m, p, b) => L.route(m, p, b || {});
const must = (r, what) => { if (r.status !== 200) throw new Error(what + ' failed: ' + r.error); return r.data; };

// ---- scenario: things a real teacher does, so the numbers are not just the plain seed
must(call('POST', '/api/auth/login', { roll: 'admin', password: 'admin' }), 'login');
must(call('POST', '/api/exams/e1/bonus', { bonus: 1.5 }), 'bonus e1');     // published exam, decimal bonus
must(call('POST', '/api/exams/e5/bonus', { bonus: 2 }), 'bonus e5');       // published exam, whole bonus
must(call('POST', '/api/exams/e2/bonus', { bonus: 0.5 }), 'bonus e2');     // NOT published
must(call('POST', '/api/exams/e4/bonus', { bonus: 1 }), 'bonus e4');
must(call('POST', '/api/exams/e4/bonus', { bonus: 0 }), 'bonus e4 removed');   // bonus set and removed again
must(call('PATCH', '/api/students/u6/enrollment', { classId: 'c10', section: 'খ' }), 'move u6'); // u6 took Class 9 exams, now in Class 10

// ---- id mapping: prototype string ids -> numbers
const num = (s) => parseInt(String(s).replace(/\D/g, ''), 10);
const userId = (id) => (id === 'a1' ? 100 : num(id));
const secIds = {};
let nextSec = 1;
d.classes.forEach(c => c.sections.forEach(s => { secIds[c.id + '|' + s] = nextSec++; }));
const q = (s) => "'" + String(s).replace(/'/g, "''") + "'";

const out = [];
out.push('-- generated from the prototype; do not edit');
out.push("INSERT INTO institutions (id, name, slug) OVERRIDING SYSTEM VALUE VALUES (1, 'Prototype Coaching', 'prototype');");
d.classes.forEach(c => out.push(`INSERT INTO school_classes (id, institution_id, name) OVERRIDING SYSTEM VALUE VALUES (${num(c.id)}, 1, ${q(c.name)});`));
d.classes.forEach(c => c.sections.forEach(s =>
  out.push(`INSERT INTO sections (id, institution_id, class_id, name) OVERRIDING SYSTEM VALUE VALUES (${secIds[c.id + '|' + s]}, 1, ${num(c.id)}, ${q(s)});`)));
d.users.forEach(u => {
  const sec = u.role === 'student' ? secIds[u.classId + '|' + u.section] : 'NULL';
  out.push(`INSERT INTO users (id, institution_id, role, name, roll, password_hash, phone, section_id) OVERRIDING SYSTEM VALUE VALUES (${userId(u.id)}, 1, ${q(u.role.toUpperCase())}, ${q(u.name)}, ${q(u.roll)}, 'x', ${u.phone ? q(u.phone) : 'NULL'}, ${sec});`);
});
let qid = 1;
const qMap = {}; // examId -> [question numeric ids]
d.exams.forEach(e => {
  const pub = e.published ? `TIMESTAMPTZ '2026-01-01 00:00:00+00' + interval '${e.pubSeq} seconds'` : 'NULL';
  out.push(`INSERT INTO exams (id, institution_id, class_id, title, duration_minutes, marks_per_question, negative_marks, bonus_marks, published_at) OVERRIDING SYSTEM VALUE VALUES (${num(e.id)}, 1, ${num(e.classId)}, ${q(e.title)}, ${e.duration}, ${e.perQ}, ${e.neg}, ${e.bonus || 0}, ${pub});`);
  qMap[e.id] = [];
  e.questions.forEach((qq, i) => {
    qMap[e.id].push(qid);
    out.push(`INSERT INTO questions (id, exam_id, sort_order, body, correct_idx) OVERRIDING SYSTEM VALUE VALUES (${qid}, ${num(e.id)}, ${i}, ${q(qq.q)}, ${qq.ans});`);
    qq.opts.forEach((o, j) => out.push(`INSERT INTO question_options (question_id, idx, label) VALUES (${qid}, ${j}, ${q(o)});`));
    qid++;
  });
});
d.attempts.forEach((a, k) => {
  const id = k + 1;
  out.push(`INSERT INTO attempts (id, institution_id, exam_id, user_id, time_sec) OVERRIDING SYSTEM VALUE VALUES (${id}, 1, ${num(a.examId)}, ${userId(a.userId)}, ${a.timeSec});`);
  if (!a.answers) throw new Error('attempt ' + a.id + ' has no answers; the prototype should have filled them');
  a.answers.forEach((ch, i) => out.push(`INSERT INTO attempt_answers (attempt_id, question_id, exam_id, chosen_idx) VALUES (${id}, ${qMap[a.examId][i]}, ${num(a.examId)}, ${ch == null ? 'NULL' : ch});`));
});
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, 'fixture.sql'), out.join('\n') + '\n');

// ---- what the prototype itself says
const expected = { exams: {}, overall: {}, counts: { users: d.users.length, exams: d.exams.length, attempts: d.attempts.length } };
d.exams.forEach(e => {
  const lb = must(call('GET', '/api/exams/' + e.id + '/leaderboard'), 'leaderboard ' + e.id);
  const nQ = e.questions.length;
  expected.exams[num(e.id)] = {
    total: lb.exam.total,
    published: e.published,
    rows: lb.rows.map(r => ({ userId: userId(r.userId), rank: r.rank, marks: r.marks, correct: r.correct, wrong: r.wrong, blank: nQ - r.correct - r.wrong, timeSec: r.timeSec })),
  };
});
// the overall board is a student-only route: log in as one student of each class
[['c9', 'u1'], ['c10', 'u7']].forEach(([cid, uid]) => {
  const roll = d.users.find(u => u.id === uid).roll;
  must(call('POST', '/api/auth/login', { roll, password: '1234' }), 'login ' + uid);
  const o = must(call('GET', '/api/leaderboard/overall'), 'overall ' + cid);
  expected.overall[num(cid)] = {
    examCount: o.examCount, full: o.full,
    rows: o.rows.map(r => ({ userId: userId(r.userId), rank: r.rank, marks: r.marks, timeSec: r.timeSec, taken: r.taken })),
  };
});
// a student sees only published exams in the list: record which exams the prototype treats as published
expected.publishedExamIds = d.exams.filter(e => e.published).map(e => num(e.id));
fs.writeFileSync(path.join(outDir, 'expected.json'), JSON.stringify(expected, null, 1));
console.log(`exported: ${d.users.length} users, ${d.exams.length} exams, ${d.attempts.length} attempts, ` +
  `${Object.values(expected.exams).reduce((n, e) => n + e.rows.length, 0)} leaderboard rows, ` +
  `${Object.values(expected.overall).reduce((n, o) => n + o.rows.length, 0)} overall rows`);
