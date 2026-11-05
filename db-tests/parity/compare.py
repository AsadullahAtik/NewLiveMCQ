"""Compares what the prototype computed (expected.json) with what the database views return (actual.json).
usage: python3 compare.py expected.json actual.json
Exit code 0 = identical on every number that is compared."""
import json
import sys
from decimal import Decimal

exp = json.load(open(sys.argv[1]))
act = json.load(open(sys.argv[2]))
D = lambda v: Decimal(str(v))
checks = fails = 0


def check(label, expected, actual):
    global checks, fails
    checks += 1
    if expected != actual:
        fails += 1
        print(f"MISMATCH {label}: prototype={expected} database={actual}")


# ---- per exam: marks, correct/wrong/blank, rank, time, full marks
for eid, e in exp["exams"].items():
    db_rows = {r["user_id"]: r for r in act["attempt_ranks"] if str(r["exam_id"]) == eid}
    check(f"exam {eid}: number of attempts", len(e["rows"]), len(db_rows))
    for r in e["rows"]:
        d = db_rows.get(r["userId"])
        tag = f"exam {eid} user {r['userId']}"
        if d is None:
            check(tag + " present", True, False)
            continue
        check(tag + " marks", D(r["marks"]), D(d["marks"]))
        check(tag + " rank", r["rank"], d["rank_no"])
        check(tag + " correct", r["correct"], d["correct"])
        check(tag + " wrong", r["wrong"], d["wrong"])
        check(tag + " blank", r["blank"], d["blank"])
        check(tag + " time", r["timeSec"], d["time_sec"])
        check(tag + " full marks", D(e["total"]), D(d["full_marks"]))
        check(tag + " marks <= full marks", True, D(d["marks"]) <= D(d["full_marks"]))
    # the order a leaderboard is shown in
    proto_order = [r["userId"] for r in e["rows"]]
    db_order = [r["user_id"] for r in sorted(db_rows.values(), key=lambda x: (x["rank_no"], x["time_sec"], x["user_id"]))]
    check(f"exam {eid}: rank/time order has the same set of users", sorted(proto_order), sorted(db_order))

# ---- published flag
db_pub = sorted(act["published_exam_ids"])
check("which exams are published", sorted(exp["publishedExamIds"]), db_pub)

# ---- overall (class-wide) leaderboard
for cid, o in exp["overall"].items():
    db_rows = {r["user_id"]: r for r in act["overall_ranks"] if str(r["class_id"]) == cid}
    check(f"overall class {cid}: students ranked", len(o["rows"]), len(db_rows))
    check(f"overall class {cid}: published exam count", o["examCount"], next(iter(db_rows.values()))["exam_count"] if db_rows else 0)
    check(f"overall class {cid}: full marks", D(o["full"]), D(next(iter(db_rows.values()))["full_marks"]) if db_rows else None)
    for r in o["rows"]:
        d = db_rows.get(r["userId"])
        tag = f"overall class {cid} user {r['userId']}"
        if d is None:
            check(tag + " present", True, False)
            continue
        check(tag + " total marks", D(r["marks"]), D(d["total_marks"]))
        check(tag + " total time", r["timeSec"], d["total_time_sec"])
        check(tag + " exams taken", r["taken"], d["exams_taken"])
        check(tag + " rank", r["rank"], d["rank_no"])

# ---- nothing extra appeared in the database
check("users in database", exp["counts"]["users"], act["counts"]["users"])
check("attempts in database", exp["counts"]["attempts"], act["counts"]["attempts"])

print(f"{checks} comparisons, {fails} mismatches")
sys.exit(1 if fails else 0)
