# AWS Security Architecture & Hardening Guide: AML Policy Guardian

## 1. Security Overview & Threat Model

The **AML Policy Guardian** processes highly sensitive financial compliance documents, SAR filings, and customer risk data. The AWS security architecture enforces **Defense-in-Depth, Zero-Trust network containment, and Least Privilege IAM access**.

### Threat Protections in AWS:
1. **Database Exposure & Data Exfiltration:** Mitigated by placing RDS PostgreSQL in private subnets with no internet gateway, no public IP, and restricting ingress strictly to the application EC2 security group.
2. **Credential Leakage in Code / Images:** Mitigated by using AWS Secrets Manager with dynamic IAM runtime injection; no secrets stored in Docker images, Git commits, or environment templates.
3. **Privilege Escalation:** Mitigated by role-based IAM instance profiles, distinct database application roles, and backend RBAC (Analyst vs. Admin).
4. **Document Tampering or Unauthorized Access:** Mitigated by S3 private bucket policies, S3 Block Public Access, SSE-KMS encryption, and TLS enforcement in transit.
5. **Prompt Injection & Model Manipulation:** Mitigated by backend input sanitization, instruction hierarchy boundaries, and retrieved-context delimiting.

---

## 2. Network Isolation & Security Groups

### Security Group Ingress / Egress Rules

#### EC2 Security Group: `sg-aml-ec2`
```json
{
  "Description": "Security group for AML Assistant EC2 host",
  "SecurityGroupIngress": [
    {
      "IpProtocol": "tcp",
      "FromPort": 80,
      "ToPort": 80,
      "CidrIp": "0.0.0.0/0",
      "Description": "HTTP for ACME challenge and 301 HTTPS redirect"
    },
    {
      "IpProtocol": "tcp",
      "FromPort": 443,
      "ToPort": 443,
      "CidrIp": "0.0.0.0/0",
      "Description": "HTTPS public frontend access"
    }
  ],
  "SecurityGroupEgress": [
    {
      "IpProtocol": "tcp",
      "FromPort": 5432,
      "ToPort": 5432,
      "DestinationSecurityGroupId": "sg-aml-rds",
      "Description": "PostgreSQL access to private RDS only"
    },
    {
      "IpProtocol": "tcp",
      "FromPort": 443,
      "ToPort": 443,
      "CidrIp": "0.0.0.0/0",
      "Description": "Outbound HTTPS for AWS APIs (S3, SecretsManager, CloudWatch, SSM) and AI LLM inference"
    }
  ]
}
```

> [!IMPORTANT]
> Port 22 (SSH) is **completely closed**. System administration is conducted exclusively through **AWS Systems Manager (SSM) Session Manager**, which authenticates via AWS IAM credentials, records terminal sessions, and requires zero open inbound firewall ports.

#### RDS Security Group: `sg-aml-rds`
```json
{
  "Description": "Security group for private AML RDS PostgreSQL",
  "SecurityGroupIngress": [
    {
      "IpProtocol": "tcp",
      "FromPort": 5432,
      "ToPort": 5432,
      "SourceSecurityGroupId": "sg-aml-ec2",
      "Description": "PostgreSQL traffic strictly from EC2 application host"
    }
  ],
  "SecurityGroupEgress": []
}
```

---

## 3. IAM Least-Privilege Policies

The EC2 instance is attached to the instance profile `aml-ec2-instance-profile` backed by role `aml-ec2-application-role`.

### 1. Document S3 Access Policy (`AMLS3DocumentAccessPolicy`)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowS3DocumentBucketActions",
      "Effect": "Allow",
      "Action": [
        "s3:ListBucket",
        "s3:GetBucketLocation"
      ],
      "Resource": "arn:aws:s3:::aml-policy-guardian-documents-*"
    },
    {
      "Sid": "AllowS3ObjectCRUD",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:GetObjectVersion"
      ],
      "Resource": "arn:aws:s3:::aml-policy-guardian-documents-*/*"
    }
  ]
}
```

### 2. CloudWatch Logs Policy (`AMLCloudWatchLogsPolicy`)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudWatchLogsIngestion",
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogStreams"
      ],
      "Resource": "arn:aws:logs:*:*:log-group:/aws/ec2/aml-assistant/*"
    }
  ]
}
```

### 3. Secrets Manager Access Policy (`AMLSecretsManagerPolicy`)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowReadApplicationSecrets",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:*:*:secret:aml/production/*"
    }
  ]
}
```

### 4. AWS Systems Manager Managed Core
* Attached managed policy: `arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore`
* Enables secure command execution and interactive sessions without SSH keys.

---

## 4. Amazon S3 Bucket Hardening & Policy

### S3 Block Public Access Configuration
All four settings are enforced:
```json
{
  "BlockPublicAcls": true,
  "IgnorePublicAcls": true,
  "BlockPublicPolicy": true,
  "RestrictPublicBuckets": true
}
```

### S3 Bucket Policy (TLS Enforcement)
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EnforceTLSRequestsOnly",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:*",
      "Resource": [
        "arn:aws:s3:::aml-policy-guardian-documents-ACCOUNT_ID-REGION",
        "arn:aws:s3:::aml-policy-guardian-documents-ACCOUNT_ID-REGION/*"
      ],
      "Condition": {
        "Bool": {
          "aws:SecureTransport": "false"
        }
      }
    }
  ]
}
```

---

## 5. Amazon RDS PostgreSQL Security & Hardening

1. **Private Subnet Placement:**
   * RDS instance is launched in a DB Subnet Group composed of subnets `10.0.10.0/24` and `10.0.11.0/24`.
   * Option `PubliclyAccessible` is set to `No`.
2. **Forced TLS Connections:**
   * Custom DB Parameter Group: `aml-pg16-hardened`
   * `rds.force_ssl = 1`
   * JDBC connection string enforces TLS: `jdbc:postgresql://<rds-endpoint>:5432/aml_assistant?sslmode=verify-full&sslrootcert=/app/certs/global-bundle.pem`
3. **Storage Encryption:**
   * Storage is encrypted using AWS KMS (`aws/rds`).
4. **Automated Backups & Point-in-Time Recovery (PITR):**
   * Backup retention period: 7 days.
   * Automated snapshot window configured during off-peak hours (03:00 - 04:00 UTC).
   * Storage autoscaling enabled (20GB up to 100GB).

---

## 6. Secrets Management Specification

Secrets are provisioned in AWS Secrets Manager under secret path `aml/production/config`:

```json
{
  "DATABASE_URL": "jdbc:postgresql://aml-postgres-db.cxxxxxx.us-east-1.rds.amazonaws.com:5432/aml_assistant?sslmode=require",
  "DATABASE_USERNAME": "aml_app_user",
  "DATABASE_PASSWORD": "GEN_STRONG_RANDOM_PASSWORD_32_CHAR",
  "JWT_SECRET": "GEN_STRONG_HEX_64_CHAR_SECRET",
  "LLM_API_KEY": "YOUR_GEMINI_OR_OPENAI_API_KEY",
  "LLM_BASE_URL": "https://generativelanguage.googleapis.com/v1beta/openai/",
  "LLM_MODEL": "gemini-1.5-flash",
  "S3_DOCUMENT_BUCKET": "aml-policy-guardian-documents-ACCOUNT_ID-REGION"
}
```

During instance boot or container deployment, the secrets are retrieved by the instance IAM role via the AWS CLI or entrypoint bootstrap script, populated directly into environment variables without ever writing plaintext passwords to git or disk.

---

## 7. HTTPS & Nginx Transport Hardening

The frontend Nginx container terminates TLS or proxies behind Certbot:

* **Protocols:** TLSv1.2, TLSv1.3 only (SSLv3, TLS 1.0, TLS 1.1 disabled).
* **Ciphers:** High-security cipher suites:
  `ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384:ECDHE-ECDSA-CHACHA20-POLY1305`
* **Security Headers:**
  * `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`
  * `X-Frame-Options: DENY`
  * `X-Content-Type-Options: nosniff`
  * `Referrer-Policy: strict-origin-when-cross-origin`
  * `Content-Security-Policy: default-src 'self'; connect-src 'self' https:; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self';`

---

## 8. Audit Logging & SIEM Integration

* **Database Level:** Immutable audit log table `audit_logs` records every transaction (user, action, timestamp, resource, correlation ID).
* **Application Level:** Spring Boot security filter logs all authentication and authorization decisions to CloudWatch Logs group `/aws/ec2/aml-assistant/backend`.
* **AWS Level:** AWS CloudTrail logs all management API calls (Secrets Manager reads, S3 access, RDS modifications).
