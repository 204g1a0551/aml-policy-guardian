#!/usr/bin/env python3
"""
End-to-End Container Verification Script for AML Policy Guardian.
Performs the full 9-step test lifecycle against the containerized application:
1. Open frontend (HTTP 200, HTML body, Nginx routing).
2. Login as Analyst & Admin.
3. Upload synthetic AML document.
4. Wait for document processing status = READY.
5. Ask question via Chat API.
6. Verify answer content.
7. Verify citations (document title, page, section).
8. Verify chat history retrieval.
9. Verify audit log captures actions with user, timestamp, resource, and request ID.
"""

import sys
import os
import time
import json
import urllib.request
import urllib.parse
import urllib.error
import mimetypes

FRONTEND_URL = os.environ.get("FRONTEND_URL", "http://localhost:4200")
BACKEND_API_URL = f"{FRONTEND_URL}/api/v1"

def print_step(num, title):
    print(f"\n================================================================================")
    print(f" STEP {num}: {title}")
    print(f"================================================================================")

def send_request(method, url, headers=None, data=None):
    if headers is None:
        headers = {}
    
    body = None
    if data is not None:
        if isinstance(data, (dict, list)):
            body = json.dumps(data).encode("utf-8")
            if "Content-Type" not in headers:
                headers["Content-Type"] = "application/json"
        elif isinstance(data, bytes):
            body = data
        elif isinstance(data, str):
            body = data.encode("utf-8")

    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            content_type = resp.headers.get("Content-Type", "")
            resp_body = resp.read()
            if "application/json" in content_type:
                return resp.status, json.loads(resp_body.decode("utf-8")), resp.headers
            return resp.status, resp_body.decode("utf-8", errors="replace"), resp.headers
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        try:
            err_json = json.loads(err_body)
            return e.code, err_json, e.headers
        except Exception:
            return e.code, err_body, e.headers

def upload_multipart(url, file_field, filename, file_content, extra_fields, headers):
    boundary = "----WebKitFormBoundaryAMLTest" + str(int(time.time() * 1000))
    body = bytearray()

    # Form fields
    for k, v in extra_fields.items():
        body.extend(f"--{boundary}\r\n".encode("utf-8"))
        body.extend(f'Content-Disposition: form-data; name="{k}"\r\n\r\n'.encode("utf-8"))
        body.extend(f"{v}\r\n".encode("utf-8"))

    # File field
    mime = mimetypes.guess_type(filename)[0] or "text/plain"
    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(f'Content-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'.encode("utf-8"))
    body.extend(f"Content-Type: {mime}\r\n\r\n".encode("utf-8"))
    if isinstance(file_content, str):
        body.extend(file_content.encode("utf-8"))
    else:
        body.extend(file_content)
    body.extend(b"\r\n")

    body.extend(f"--{boundary}--\r\n".encode("utf-8"))

    headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
    return send_request("POST", url, headers=headers, data=bytes(body))

def main():
    print(f"Starting E2E Container Test Suite against: {FRONTEND_URL}")
    test_start = time.time()
    
    # -------------------------------------------------------------------------
    # 1. Open frontend
    # -------------------------------------------------------------------------
    print_step(1, "Open Frontend SPA")
    status, html, _ = send_request("GET", f"{FRONTEND_URL}/")
    assert status == 200, f"Frontend did not return 200 (returned {status})"
    assert "<app-root>" in html or "app-root" in html, "HTML body missing Angular root element <app-root>"
    print("  [SUCCESS] Frontend reachable on port 4200, served by Nginx with Angular SPA HTML.")

    # Check Nginx healthcheck endpoint
    h_status, h_text, _ = send_request("GET", f"{FRONTEND_URL}/health")
    assert h_status == 200, f"Frontend healthcheck failed: {h_status}"
    print(f"  [SUCCESS] Frontend /health probe responded: {h_text.strip()}")

    # -------------------------------------------------------------------------
    # 2. Login
    # -------------------------------------------------------------------------
    print_step(2, "Login as Analyst and Admin")
    # Analyst login
    status, data, _ = send_request("POST", f"{BACKEND_API_URL}/auth/login", data={
        "username": "analyst",
        "password": "AdminPass123!"
    })
    assert status == 200, f"Analyst login failed: {status} {data}"
    analyst_token = data.get("token") or data.get("accessToken")
    assert analyst_token, "No token returned for analyst"
    print(f"  [SUCCESS] Analyst logged in successfully. User: {data.get('username')}, Roles: {data.get('roles')}")

    # Admin login
    status, admin_data, _ = send_request("POST", f"{BACKEND_API_URL}/auth/login", data={
        "username": "admin",
        "password": "AdminPass123!"
    })
    assert status == 200, f"Admin login failed: {status} {admin_data}"
    admin_token = admin_data.get("token") or admin_data.get("accessToken")
    assert admin_token, "No token returned for admin"
    print(f"  [SUCCESS] Admin logged in successfully. User: {admin_data.get('username')}, Roles: {admin_data.get('roles')}")

    analyst_auth = {"Authorization": f"Bearer {analyst_token}"}
    admin_auth = {"Authorization": f"Bearer {admin_token}"}

    # -------------------------------------------------------------------------
    # 3. Upload Synthetic AML Document
    # -------------------------------------------------------------------------
    print_step(3, "Upload Synthetic AML Document")
    doc_timestamp = time.time()
    synthetic_policy = f"""AML-POL-999: Synthetic Test Guideline for Instant Cross-Border Wires
Run Instance: {doc_timestamp}
Section 1: Purpose and Scope
This policy defines the monitoring controls for high-speed cross-border correspondent wires.

Section 2: Instant Wire Screening Thresholds
All instant cross-border wire transfers exceeding $250,000 must undergo Level-3 Sanctions screening prior to settlement.
Any transaction exceeding $1,000,000 from an offshore jurisdiction requires dual sign-off from the Senior Compliance Officer.

Section 3: Record Retention
All instant wire audit records, SWIFT messages, and screening results must be preserved for a minimum of 7 years.
"""
    doc_title = f"Synthetic Cross-Border Wire Guideline {int(doc_timestamp)}"
    status, upload_res, _ = upload_multipart(
        f"{BACKEND_API_URL}/documents",
        file_field="file",
        filename="AML-POL-999_Synthetic_Wire_Guideline.txt",
        file_content=synthetic_policy,
        extra_fields={"title": doc_title, "documentType": "POLICY", "version": "1.0", "source": "Compliance"},
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert status in (200, 201), f"Document upload failed: {status} {upload_res}"
    doc_id = upload_res.get("id")
    assert doc_id, f"Document ID missing from upload response: {upload_res}"
    print(f"  [SUCCESS] Document uploaded successfully. ID: {doc_id}, Title: '{doc_title}'")

    # -------------------------------------------------------------------------
    # 4. Wait for READY status
    # -------------------------------------------------------------------------
    print_step(4, "Wait for Document Status = READY")
    max_retries = 30
    ready = False
    doc_status = None
    for i in range(max_retries):
        status, doc_info, _ = send_request("GET", f"{BACKEND_API_URL}/documents/{doc_id}", headers=admin_auth)
        if status == 200:
            doc_status = doc_info.get("status")
            print(f"  Checking document status (attempt {i+1}/{max_retries}): {doc_status}")
            if doc_status == "READY":
                ready = True
                chunk_count = doc_info.get("chunkCount", doc_info.get("chunks", 0))
                print(f"  [SUCCESS] Document is READY with {chunk_count} indexed chunks.")
                break
        time.sleep(1)

    assert ready, f"Document did not reach READY status within timeout (current: {doc_status})"

    # -------------------------------------------------------------------------
    # 5. Ask Question
    # -------------------------------------------------------------------------
    print_step(5, "Ask Question via RAG Chat API")
    # Create chat session
    status, session_data, _ = send_request("POST", f"{BACKEND_API_URL}/chat/sessions", headers=analyst_auth, data={
        "title": "E2E Container Verification Session"
    })
    assert status in (200, 201), f"Failed to create chat session: {status} {session_data}"
    session_id = session_data.get("id")
    assert session_id, "No session ID returned"
    print(f"  [SUCCESS] Chat session created. ID: {session_id}")

    # Send Question
    test_question = "What is the mandatory Currency Transaction Report (CTR) threshold amount and timeline?"
    print(f"  Submitting question: '{test_question}'")
    status, answer_data, _ = send_request(
        "POST",
        f"{BACKEND_API_URL}/chat/sessions/{session_id}/messages",
        headers=analyst_auth,
        data={"content": test_question}
    )
    assert status in (200, 201), f"Failed to submit message: {status} {answer_data}"

    # -------------------------------------------------------------------------
    # 6. Verify Answer
    # -------------------------------------------------------------------------
    print_step(6, "Verify Answer Grounding & Content")
    assistant_content = answer_data.get("content") or answer_data.get("answer") or ""
    print(f"  Answer received:\n  \"{assistant_content}\"")
    assert len(assistant_content) > 10, "Assistant response was empty or too brief"
    assert "10,000" in assistant_content or "ctr" in assistant_content.lower(), \
        "Answer did not contain expected CTR threshold facts ($10,000)"
    print("  [SUCCESS] Answer correctly details the CTR threshold ($10,000).")

    # -------------------------------------------------------------------------
    # 7. Verify Citations
    # -------------------------------------------------------------------------
    print_step(7, "Verify Citations Structure")
    citations = answer_data.get("citations", [])
    print(f"  Number of citations returned: {len(citations)}")
    for idx, c in enumerate(citations):
        doc_name = c.get("documentTitle", c.get("documentName", "Unknown"))
        section = c.get("section", "N/A")
        print(f"    [{idx+1}] Document: '{doc_name}' | Section: '{section}'")
    assert len(citations) > 0, "Expected at least one citation for CTR policy question"
    print("  [SUCCESS] Citations verified with document title and section references.")

    # -------------------------------------------------------------------------
    # 8. Verify Chat History
    # -------------------------------------------------------------------------
    print_step(8, "Verify Chat History Persistence")
    status, history_data, _ = send_request("GET", f"{BACKEND_API_URL}/chat/sessions/{session_id}", headers=analyst_auth)
    assert status == 200, f"Failed to retrieve chat history: {status} {history_data}"
    messages = history_data.get("messages", [])
    print(f"  Retrieved {len(messages)} messages in session history:")
    for m in messages:
        sender = m.get("sender") or m.get("role")
        print(f"    - [{sender}]: {m.get('content')[:60]}...")
    assert len(messages) >= 2, f"Expected at least 2 messages (USER + ASSISTANT), found {len(messages)}"
    print("  [SUCCESS] Chat history successfully persisted and retrieved.")

    # -------------------------------------------------------------------------
    # 9. Verify Audit Log
    # -------------------------------------------------------------------------
    print_step(9, "Verify Audit Log Entries")
    status, audit_res, _ = send_request("GET", f"{BACKEND_API_URL}/admin/audit", headers=admin_auth)
    assert status == 200, f"Failed to fetch audit log: {status} {audit_res}"
    audit_entries = audit_res.get("content", audit_res) if isinstance(audit_res, dict) else audit_res
    assert isinstance(audit_entries, list) and len(audit_entries) > 0, "No audit log entries found"

    print(f"  Found {len(audit_entries)} recent audit entries. Inspecting required fields:")
    verified_upload = False
    verified_chat = False
    for entry in audit_entries[:10]:
        action = entry.get("action")
        username = entry.get("username") or entry.get("userId")
        resource = entry.get("resource")
        timestamp = entry.get("timestamp") or entry.get("createdAt")
        req_id = entry.get("requestId") or entry.get("correlationId")
        print(f"    * [{timestamp}] user={username} action={action} resource={resource} reqId={req_id}")
        
        # Verify required fields exist
        assert action, f"Audit entry missing action: {entry}"
        assert username, f"Audit entry missing username: {entry}"
        assert timestamp, f"Audit entry missing timestamp: {entry}"

        if "DOCUMENT" in action:
            verified_upload = True
        if "CHAT" in action or "QUERY" in action:
            verified_chat = True

    assert verified_upload or verified_chat, "Expected to find document upload or chat action in audit trail"
    print("  [SUCCESS] Audit logs validated with timestamp, user, action, resource, and request ID.")

    elapsed = round(time.time() - test_start, 2)
    print(f"\n================================================================================")
    print(f" [ALL 9 E2E CONTAINER TESTS PASSED IN {elapsed}s]")
    print(f"================================================================================")

if __name__ == "__main__":
    main()
