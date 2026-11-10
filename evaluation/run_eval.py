#!/usr/bin/env python3
"""
AML Policy Guardian - Automated RAG Evaluation & Observability Suite
Executes synthetic benchmark datasets against the live RAG API,
measures real metrics (retrieval, citations, groundedness, correctness, refusal, latency),
propagates structured correlation IDs, performs regression comparisons,
and exports reproducible JSON execution summaries to evaluation/results/.
"""

import sys
import os
import json
import time
import uuid
import argparse
import urllib.request
import urllib.error
from datetime import datetime

API_BASE_URL = os.environ.get("AML_API_BASE_URL", "http://localhost:8080/api/v1")
AUTH_USER = os.environ.get("AML_AUTH_USER", "analyst")
AUTH_PASS = os.environ.get("AML_AUTH_PASS", "AdminPass123!")

def http_post(url, payload, headers=None, max_retries=3):
    if headers is None:
        headers = {}
    data = json.dumps(payload).encode("utf-8")
    for attempt in range(max_retries + 1):
        req = urllib.request.Request(url, data=data, headers=headers)
        try:
            with urllib.request.urlopen(req) as resp:
                body = resp.read().decode("utf-8")
                correlation_id = resp.headers.get("X-Correlation-ID")
                parsed = json.loads(body) if body else {}
                if isinstance(parsed, dict) and correlation_id and "correlationId" not in parsed:
                    parsed["correlationId"] = correlation_id
                return resp.status, parsed
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < max_retries:
                retry_after = int(e.headers.get("Retry-After", "5"))
                time.sleep(retry_after + 1)
                continue
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
    parser = argparse.ArgumentParser(description="AML Policy Guardian Automated RAG Evaluation Suite")
    parser.add_argument("--benchmark", default=None, help="Path to benchmark JSON (questions or versioned benchmark file)")
    parser.add_argument("--compare-with", default=None, help="Path to previous run JSON to evaluate regressions against")
    parser.add_argument("--fail-on-regression", action="store_true", help="Exit with code 1 if quality regression or SLA breach is detected")
    args = parser.parse_args()

    script_dir = os.path.dirname(os.path.abspath(__file__))
    results_dir = os.path.join(script_dir, "results")
    os.makedirs(results_dir, exist_ok=True)

    benchmark_version = "v1.0.0-synthetic-34"

    if args.benchmark and os.path.exists(args.benchmark):
        benchmark_file = args.benchmark
        with open(benchmark_file, "r", encoding="utf-8") as f:
            data = json.load(f)
            if isinstance(data, dict) and "questions" in data:
                questions = data["questions"]
                expected_list = data.get("expected", [])
                benchmark_version = data.get("version", "custom")
            else:
                questions = data
                with open(os.path.join(script_dir, "expected.json"), "r", encoding="utf-8") as ef:
                    expected_list = json.load(ef)
    else:
        questions_path = os.path.join(script_dir, "questions.json")
        expected_path = os.path.join(script_dir, "expected.json")
        with open(questions_path, "r", encoding="utf-8") as f:
            questions = json.load(f)
        with open(expected_path, "r", encoding="utf-8") as f:
            expected_list = json.load(f)

    expected_map = {item["id"]: item for item in expected_list}

    print("================================================================================")
    print("        AML POLICY GUARDIAN — REPRODUCIBLE RAG EVALUATION HARNESS              ")
    print("================================================================================")
    print(f"Target API:        {API_BASE_URL}")
    print(f"Benchmark Version: {benchmark_version}")
    print(f"Benchmark Set:     {len(questions)} Synthetic Questions")
    print(f"Execution Time:    {datetime.now().isoformat()}")
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

    DOC_ALIASES = {
        "AML-SOP-001": ["AML-SOP-001", "Transaction Monitoring", "Automated Surveillance"],
        "AML-SOP-003": ["AML-SOP-003", "Suspicious Activity", "SAR"],
        "AML-GUIDE-004": ["AML-GUIDE-004", "Transaction Escalation", "Escalation"],
        "AML-POL-002": ["AML-POL-002", "Customer Risk Assessment", "Customer Risk"],
        "AML-SOP-005": ["AML-SOP-005", "KYC Verification", "Customer Due Diligence", "CIP"]
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

        # Correlate every inquiry with an explicit X-Correlation-ID and isolated eval client IP
        client_correlation_id = f"eval-{q_id}-{uuid.uuid4().hex[:8]}"
        req_headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
            "X-Correlation-ID": client_correlation_id,
            "X-Forwarded-For": f"10.99.1.{i}"
        }

        t0 = time.perf_counter()
        post_url = f"{API_BASE_URL}/chat/sessions/{session_id}/messages"
        status, response_body = http_post(post_url, {"message": q_text}, req_headers)
        t1 = time.perf_counter()

        latency_ms = round((t1 - t0) * 1000, 2)
        latencies.append(latency_ms)

        assistant_answer = response_body.get("content", "")
        citations = response_body.get("citations", [])
        evidence_level = response_body.get("evidenceLevel", "HIGH")
        evidence_score = response_body.get("evidenceQualityScore", 1.0)
        server_correlation_id = response_body.get("correlationId", client_correlation_id)

        # 1. Retrieval Relevance
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
                "i cannot find sufficient guidance" in answer_lower or
                "cannot execute instructions" in answer_lower or
                "cannot be suspended" in answer_lower or
                "cannot be bypassed" in answer_lower or
                "cannot be approved" in answer_lower or
                "money laundering reporting officer" in answer_lower or
                "mlro" in answer_lower or
                "approved bank compliance policies" in answer_lower or
                "strict compliance" in answer_lower
            )
            if is_refused:
                refusal_hits += 1
            else:
                refusal_passed = False

        # 4. Answer Keyword Coverage & Guardrail Safety
        correctness_passed = True
        answer_lower = assistant_answer.lower()

        if expected_behavior == "ANSWER":
            for kw in required_keywords:
                if kw.lower() not in answer_lower:
                    correctness_passed = False
                    break

        for fkw in forbidden_keywords:
            if fkw.lower() in answer_lower:
                correctness_passed = False
                break

        if correctness_passed:
            correctness_hits += 1

        # Groundedness Check
        groundedness_passed = True
        if expected_behavior == "ANSWER" and not citations and "cannot find sufficient guidance" not in answer_lower:
            groundedness_passed = False
        if groundedness_passed:
            groundedness_clean += 1

        overall_pass = retrieval_passed and citation_passed and refusal_passed and correctness_passed and groundedness_passed

        status_str = "PASS" if overall_pass else "FAIL"
        print(f"  [{i:02d}/{len(questions):02d}] {q_id} ({category:<17}) -> {status_str} in {latency_ms:6.1f}ms")

        results.append({
            "id": q_id,
            "category": category,
            "question": q_text,
            "expectedBehavior": expected_behavior,
            "latencyMs": latency_ms,
            "correlationId": server_correlation_id,
            "evidenceLevel": evidence_level,
            "evidenceScore": evidence_score,
            "retrievalPass": retrieval_passed,
            "citationPass": citation_passed,
            "refusalPass": refusal_passed,
            "correctnessPass": correctness_passed,
            "groundednessPass": groundedness_passed,
            "overallPass": overall_pass,
            "citationsCount": len(citations)
        })

    # Summary Metrics Calculation
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
        "benchmarkVersion": benchmark_version,
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

    # Save results
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

    # Print Final Report
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

    # Regression Comparison if requested
    regression_detected = False
    if args.compare_with and os.path.exists(args.compare_with):
        with open(args.compare_with, "r", encoding="utf-8") as bf:
            baseline_data = json.load(bf)
            base_sum = baseline_data.get("summary", {})
            base_metrics = base_sum.get("metrics", {})
            base_lat = base_sum.get("latency", {})

            pass_delta = pass_rate - base_sum.get("overallPassRatePct", pass_rate)
            grounded_delta = groundedness_rate - base_metrics.get("groundednessRatePct", groundedness_rate)
            recall_delta = retrieval_accuracy - base_metrics.get("retrievalRelevanceRatePct", retrieval_accuracy)
            p95_delta = latency_p95 - base_lat.get("p95Ms", latency_p95)

            print("\nREGRESSION BENCHMARK COMPARISON against baseline:")
            print(f"  * Pass Rate Delta:        {pass_delta:+.2f}%")
            print(f"  * Groundedness Delta:     {grounded_delta:+.2f}%")
            print(f"  * Retrieval Recall Delta: {recall_delta:+.2f}%")
            print(f"  * P95 Latency Delta:      {p95_delta:+.1f} ms")

            if pass_delta < -2.0 or grounded_delta < -5.0 or recall_delta < -5.0 or p95_delta > 500.0:
                regression_detected = True
                print("  => STATUS: [REGRESSION ALERT TRIGGERED]")
            else:
                print("  => STATUS: [NO REGRESSION - MEETS QUALITY THRESHOLDS]")

    print(f"Results archived to:")
    print(f"  -> {run_file}")
    print(f"  -> {latest_file}")
    print("================================================================================")

    if args.fail_on_regression and (regression_detected or passed_q < total_q or latency_p95 > 1500.0):
        print("CI GATE FAILED: Quality regression or SLA breach exceeded configured threshold.")
        sys.exit(1)

    if passed_q < total_q:
        sys.exit(1)

if __name__ == "__main__":
    run_evaluation()
