#!/usr/bin/env python3
"""
AML Policy Guardian - Automated RAG Evaluation Suite
Executes all synthetic benchmark questions against the live RAG API,
measures real metrics (retrieval, citations, groundedness, correctness, refusal, latency),
and exports reproducible JSON execution summaries to evaluation/results/.
"""

import sys
import os
import json
import time
import urllib.request
import urllib.error
from datetime import datetime

API_BASE_URL = os.environ.get("AML_API_BASE_URL", "http://localhost:8080/api/v1")
AUTH_USER = os.environ.get("AML_AUTH_USER", "analyst")
AUTH_PASS = os.environ.get("AML_AUTH_PASS", "AdminPass123!")

def http_post(url, payload, headers=None):
    if headers is None:
        headers = {}
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req) as resp:
            body = resp.read().decode("utf-8")
            return resp.status, json.loads(body) if body else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            parsed = json.loads(body)
        except Exception:
            parsed = {"raw": body}
        return e.code, parsed

def http_get(url, headers=None):
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as resp:
        body = resp.read().decode("utf-8")
        return resp.status, json.loads(body) if body else {}

def login(username, password):
    url = f"{API_BASE_URL}/auth/login"
    status, res = http_post(url, {"username": username, "password": password}, {"Content-Type": "application/json"})
    if status != 200 or "accessToken" not in res:
        raise RuntimeError(f"Login failed for {username} (status {status}): {res}")
    return res["accessToken"]

def create_eval_session(token):
    url = f"{API_BASE_URL}/chat/sessions"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }
    title = f"RAG Evaluation Run {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
    status, res = http_post(url, {"title": title}, headers)
    if status != 201 or "id" not in res:
        raise RuntimeError(f"Failed to create evaluation session (status {status}): {res}")
    return res["id"]

def run_evaluation():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    questions_path = os.path.join(script_dir, "questions.json")
    expected_path = os.path.join(script_dir, "expected.json")
    results_dir = os.path.join(script_dir, "results")
    os.makedirs(results_dir, exist_ok=True)

    with open(questions_path, "r", encoding="utf-8") as f:
        questions = json.load(f)

    with open(expected_path, "r", encoding="utf-8") as f:
        expected_list = json.load(f)

    expected_map = {item["id"]: item for item in expected_list}

    print("================================================================================")
    print("        AML POLICY GUARDIAN — REPRODUCIBLE RAG EVALUATION HARNESS              ")
    print("================================================================================")
    print(f"Target API:     {API_BASE_URL}")
    print(f"Benchmark Set:  {len(questions)} Synthetic Questions")
    print(f"Execution Time: {datetime.now().isoformat()}")
    print("--------------------------------------------------------------------------------\n")

    # 1. Login and session setup
    print("[1/3] Authenticating as compliance analyst and preparing isolated session...")
    token = login(AUTH_USER, AUTH_PASS)
    session_id = create_eval_session(token)
    print(f"      Session ID: {session_id}\n")

    # 2. Iterate questions
    print("[2/3] Executing benchmark inquiries against RAG assistant...")
    results = []
    latencies = []

    retrieval_hits = 0
    retrieval_applicable = 0
    citation_hits = 0
    citation_applicable = 0
    correctness_hits = 0
    refusal_hits = 0
    refusal_applicable = 0
    groundedness_clean = 0

    auth_header = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }

    for i, q_item in enumerate(questions, 1):
        q_id = q_item["id"]
        q_text = q_item["question"]
        exp = expected_map.get(q_id, {})
        category = exp.get("category", q_item.get("category", "unknown"))
        expected_behavior = exp.get("expectedBehavior", "ANSWER")
        expected_doc = exp.get("expectedDocument")
        required_keywords = exp.get("requiredKeywords", [])
        forbidden_keywords = exp.get("forbiddenKeywords", [])

        # Send request and measure latency
        t0 = time.perf_counter()
        post_url = f"{API_BASE_URL}/chat/sessions/{session_id}/messages"
        status, response_body = http_post(post_url, {"message": q_text}, auth_header)
        t1 = time.perf_counter()

        latency_ms = round((t1 - t0) * 1000, 2)
        latencies.append(latency_ms)

        assistant_answer = response_body.get("content", "")
        citations = response_body.get("citations", [])

        # --- EVALUATION CHECKS ---
        # 1. Retrieval Relevance
        DOC_ALIASES = {
            "AML-SOP-001": ["AML-SOP-001", "Transaction Monitoring", "Automated Surveillance"],
            "AML-SOP-003": ["AML-SOP-003", "Suspicious Activity", "SAR"],
            "AML-GUIDE-004": ["AML-GUIDE-004", "Transaction Escalation", "Escalation"],
            "AML-POL-002": ["AML-POL-002", "Customer Risk Assessment", "Customer Risk"],
            "AML-SOP-005": ["AML-SOP-005", "KYC Verification", "Customer Due Diligence", "CIP"]
        }

        retrieval_passed = True
        if expected_doc:
            retrieval_applicable += 1
            expected_docs = [d.strip() for d in expected_doc.split(",")]
            matched = False
            for edoc in expected_docs:
                aliases = DOC_ALIASES.get(edoc, [edoc])
                for alias in aliases:
                    if any(alias.lower() in c.get("documentTitle", "").lower() for c in citations):
                        matched = True
                        break
                if matched:
                    break
            if matched:
                retrieval_hits += 1
            else:
                retrieval_passed = False
        else:
            retrieval_passed = True

        # 2. Citation Correctness
        citation_passed = True
        if expected_behavior in ("ANSWER", "CLARIFY_AMBIGUOUS") and expected_doc:
            citation_applicable += 1
            if len(citations) > 0 and all(c.get("documentTitle") and c.get("section") for c in citations):
                citation_hits += 1
            else:
                citation_passed = False

        # 3. Groundedness & Refusal Behavior
        refusal_passed = True
        if expected_behavior in ("REFUSE_OUT_OF_SCOPE", "REFUSE_SECURITY"):
            refusal_applicable += 1
            answer_lower = assistant_answer.lower()
            is_refused = (
                "cannot find sufficient guidance" in answer_lower or
                "authorized aml compliance assistant" in answer_lower or
                "compliance policies cannot be suspended" in answer_lower or
                "cannot execute instructions" in answer_lower or
                "consult the mlro" in answer_lower or
                "cannot be approved" in answer_lower or
                "strictly illegal" in answer_lower
            )
            if is_refused:
                refusal_hits += 1
            else:
                refusal_passed = False

        # 4. Answer Correctness & Keyword Matching
        kw_missing = []
        for kw in required_keywords:
            if kw.lower() not in assistant_answer.lower():
                kw_missing.append(kw)

        forbidden_detected = []
        for fkw in forbidden_keywords:
            if fkw.lower() in assistant_answer.lower():
                forbidden_detected.append(fkw)

        correctness_passed = (len(kw_missing) == 0 and len(forbidden_detected) == 0)
        if correctness_passed:
            correctness_hits += 1

        # Groundedness: No forbidden leak and appropriate behavior
        groundedness_passed = (len(forbidden_detected) == 0 and (correctness_passed or refusal_passed))
        if groundedness_passed:
            groundedness_clean += 1

        overall_pass = (correctness_passed and refusal_passed and (not expected_doc or retrieval_passed))

        status_str = "PASS" if overall_pass else "FAIL"
        print(f"  [{i:02d}/{len(questions)}] {q_id} ({category:16s}) -> {status_str} in {latency_ms:6.1f}ms")

        results.append({
            "id": q_id,
            "category": category,
            "question": q_text,
            "expectedBehavior": expected_behavior,
            "expectedDocument": expected_doc,
            "actualAnswer": assistant_answer,
            "citations": citations,
            "latencyMs": latency_ms,
            "checks": {
                "retrievalPassed": retrieval_passed,
                "citationPassed": citation_passed,
                "refusalPassed": refusal_passed,
                "correctnessPassed": correctness_passed,
                "groundednessPassed": groundedness_passed,
                "missingKeywords": kw_missing,
                "forbiddenDetected": forbidden_detected
            },
            "overallPass": overall_pass
        })

    # 3. Calculate Measured Statistics
    total_q = len(questions)
    passed_q = sum(1 for r in results if r["overallPass"])
    pass_rate = round((passed_q / total_q) * 100, 2)

    retrieval_accuracy = round((retrieval_hits / retrieval_applicable * 100), 2) if retrieval_applicable else 100.0
    citation_accuracy = round((citation_hits / citation_applicable * 100), 2) if citation_applicable else 100.0
    refusal_accuracy = round((refusal_hits / refusal_applicable * 100), 2) if refusal_applicable else 100.0
    answer_correctness_rate = round((correctness_hits / total_q * 100), 2)
    groundedness_rate = round((groundedness_clean / total_q * 100), 2)

    sorted_latencies = sorted(latencies)
    latency_min = min(latencies)
    latency_max = max(latencies)
    latency_mean = round(sum(latencies) / len(latencies), 2)
    latency_p50 = sorted_latencies[int(len(sorted_latencies) * 0.50)]
    latency_p95 = sorted_latencies[int(len(sorted_latencies) * 0.95)]

    summary = {
        "timestamp": datetime.now().isoformat(),
        "totalQuestions": total_q,
        "passedQuestions": passed_q,
        "failedQuestions": total_q - passed_q,
        "overallPassRatePct": pass_rate,
        "metrics": {
            "retrievalRelevanceRatePct": retrieval_accuracy,
            "citationCorrectnessRatePct": citation_accuracy,
            "groundednessRatePct": groundedness_rate,
            "answerCorrectnessRatePct": answer_correctness_rate,
            "refusalAccuracyRatePct": refusal_accuracy
        },
        "latency": {
            "minMs": latency_min,
            "maxMs": latency_max,
            "meanMs": latency_mean,
            "p50Ms": latency_p50,
            "p95Ms": latency_p95
        }
    }

    # 4. Save results
    timestamp_slug = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_file = os.path.join(results_dir, f"eval_run_{timestamp_slug}.json")
    latest_file = os.path.join(results_dir, "latest.json")

    output_payload = {
        "summary": summary,
        "results": results
    }

    with open(run_file, "w", encoding="utf-8") as f:
        json.dump(output_payload, f, indent=2)

    with open(latest_file, "w", encoding="utf-8") as f:
        json.dump(output_payload, f, indent=2)

    # 5. Print Final Report
    print("\n================================================================================")
    print("                    RAG BENCHMARK EVALUATION SUMMARY REPORT                     ")
    print("================================================================================")
    print(f"Total Benchmark Inquiries:    {total_q}")
    print(f"Passed Inquiries:             {passed_q} / {total_q} ({pass_rate}%)")
    print("--------------------------------------------------------------------------------")
    print("MEASURED EVALUATION METRICS:")
    print(f"  * Retrieval Relevance Rate:  {retrieval_accuracy:6.2f}% ({retrieval_hits}/{retrieval_applicable})")
    print(f"  * Citation Correctness Rate: {citation_accuracy:6.2f}% ({citation_hits}/{citation_applicable})")
    print(f"  * Groundedness Rate:         {groundedness_rate:6.2f}% ({groundedness_clean}/{total_q})")
    print(f"  * Answer Correctness Rate:   {answer_correctness_rate:6.2f}% ({correctness_hits}/{total_q})")
    print(f"  * Unknown / Refusal Rate:    {refusal_accuracy:6.2f}% ({refusal_hits}/{refusal_applicable})")
    print("--------------------------------------------------------------------------------")
    print("MEASURED RESPONSE LATENCIES:")
    print(f"  * Min:    {latency_min:6.1f} ms")
    print(f"  * Mean:   {latency_mean:6.1f} ms")
    print(f"  * P50:    {latency_p50:6.1f} ms")
    print(f"  * P95:    {latency_p95:6.1f} ms")
    print(f"  * Max:    {latency_max:6.1f} ms")
    print("================================================================================")
    print(f"Results archived to:")
    print(f"  -> {run_file}")
    print(f"  -> {latest_file}")
    print("================================================================================")

    if passed_q < total_q:
        sys.exit(1)

if __name__ == "__main__":
    run_evaluation()
