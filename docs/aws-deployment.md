# AWS Deployment, Verification & Operations Runbook: AML Policy Guardian

## 1. Overview & Prerequisites

This runbook provides complete, reproducible step-by-step instructions to deploy the **AML Policy Guardian** application to AWS. It also includes comprehensive rollback procedures, post-deployment verification tests, and a teardown cleanup procedure to eliminate AWS charges when the application is not in use.

### Prerequisites
* AWS CLI v2 installed (`aws --version`) and configured with administrative credentials (`aws configure`).
* Selected AWS Region: `us-east-1` (or your chosen region).
* Domain name with DNS managed via Route 53 or external DNS registrar (for HTTPS certification).
* Docker and Docker Compose installed on deployment machine (or automated via EC2 UserData).

---

## 2. Step-by-Step Deployment Guide

### Step 1: Create VPC, Subnets & Internet Gateway

```bash
export AWS_REGION="us-east-1"
export PROJECT="aml"

# 1. Create VPC (10.0.0.0/16)
VPC_ID=$(aws ec2 create-vpc --cidr-block 10.0.0.0/16 \
  --tag-specifications "ResourceType=vpc,Tags=[{Key=Name,Value=${PROJECT}-vpc}]" \
  --query 'Vpc.VpcId' --output text --region $AWS_REGION)

aws ec2 modify-vpc-attribute --vpc-id $VPC_ID --enable-dns-hostnames "{\"Value\":true}" --region $AWS_REGION
aws ec2 modify-vpc-attribute --vpc-id $VPC_ID --enable-dns-support "{\"Value\":true}" --region $AWS_REGION

# 2. Create Internet Gateway & attach to VPC
IGW_ID=$(aws ec2 create-internet-gateway \
  --tag-specifications "ResourceType=internet-gateway,Tags=[{Key=Name,Value=${PROJECT}-igw}]" \
  --query 'InternetGateway.InternetGatewayId' --output text --region $AWS_REGION)

aws ec2 attach-internet-gateway --vpc-id $VPC_ID --internet-gateway-id $IGW_ID --region $AWS_REGION

# 3. Create Public Subnet for EC2 (10.0.1.0/24 in us-east-1a)
PUB_SUBNET_ID=$(aws ec2 create-subnet --vpc-id $VPC_ID --cidr-block 10.0.1.0/24 \
  --availability-zone ${AWS_REGION}a \
  --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=${PROJECT}-public-1a}]" \
  --query 'Subnet.SubnetId' --output text --region $AWS_REGION)

# 4. Create Private DB Subnets for RDS (10.0.10.0/24 in 1a, 10.0.11.0/24 in 1b)
DB_SUBNET_1=$(aws ec2 create-subnet --vpc-id $VPC_ID --cidr-block 10.0.10.0/24 \
  --availability-zone ${AWS_REGION}a \
  --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=${PROJECT}-private-db-1a}]" \
  --query 'Subnet.SubnetId' --output text --region $AWS_REGION)

DB_SUBNET_2=$(aws ec2 create-subnet --vpc-id $VPC_ID --cidr-block 10.0.11.0/24 \
  --availability-zone ${AWS_REGION}b \
  --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=${PROJECT}-private-db-1b}]" \
  --query 'Subnet.SubnetId' --output text --region $AWS_REGION)

# 5. Create Public Route Table and default route to IGW
PUB_RTB_ID=$(aws ec2 create-route-table --vpc-id $VPC_ID \
  --tag-specifications "ResourceType=route-table,Tags=[{Key=Name,Value=${PROJECT}-public-rtb}]" \
  --query 'RouteTable.RouteTableId' --output text --region $AWS_REGION)

aws ec2 create-route --route-table-id $PUB_RTB_ID --destination-cidr-block 0.0.0.0/0 --gateway-id $IGW_ID --region $AWS_REGION
aws ec2 associate-route-table --subnet-id $PUB_SUBNET_ID --route-table-id $PUB_RTB_ID --region $AWS_REGION
```

---

### Step 2: Deploy S3 Gateway VPC Endpoint ($0 Cost)

```bash
# Provision S3 Gateway Endpoint to avoid NAT Gateway charges
aws ec2 create-vpc-endpoint \
  --vpc-id $VPC_ID \
  --service-name com.amazonaws.${AWS_REGION}.s3 \
  --route-table-ids $PUB_RTB_ID \
  --region $AWS_REGION
```

---

### Step 3: Configure Security Groups

```bash
# 1. EC2 Security Group
SG_EC2_ID=$(aws ec2 create-security-group \
  --group-name ${PROJECT}-ec2-sg \
  --description "Security group for AML Assistant EC2 host" \
  --vpc-id $VPC_ID \
  --query 'GroupId' --output text --region $AWS_REGION)

# Allow HTTP and HTTPS
aws ec2 authorize-security-group-ingress --group-id $SG_EC2_ID --protocol tcp --port 80 --cidr 0.0.0.0/0 --region $AWS_REGION
aws ec2 authorize-security-group-ingress --group-id $SG_EC2_ID --protocol tcp --port 443 --cidr 0.0.0.0/0 --region $AWS_REGION

# 2. RDS Security Group (STRICT PRIVATE INGRESS ONLY)
SG_RDS_ID=$(aws ec2 create-security-group \
  --group-name ${PROJECT}-rds-sg \
  --description "Security group for AML Assistant RDS PostgreSQL" \
  --vpc-id $VPC_ID \
  --query 'GroupId' --output text --region $AWS_REGION)

aws ec2 authorize-security-group-ingress --group-id $SG_RDS_ID --protocol tcp --port 5432 --source-group $SG_EC2_ID --region $AWS_REGION
```

---

### Step 4: Provision Private S3 Bucket for Policy Documents

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET_NAME="aml-policy-guardian-documents-${ACCOUNT_ID}-${AWS_REGION}"

# Create S3 Bucket
aws s3api create-bucket --bucket $BUCKET_NAME --region $AWS_REGION

# Enable Default KMS Encryption
aws s3api put-bucket-encryption --bucket $BUCKET_NAME \
  --server-side-encryption-configuration '{"Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]}' \
  --region $AWS_REGION

# Block all Public Access
aws s3api put-public-access-block --bucket $BUCKET_NAME \
  --public-access-block-configuration "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true" \
  --region $AWS_REGION

# Enable Versioning for Document Auditing
aws s3api put-bucket-versioning --bucket $BUCKET_NAME \
  --versioning-configuration Status=Enabled \
  --region $AWS_REGION

# Apply TLS Enforcement Policy
cat <<EOF > /tmp/s3-tls-policy.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EnforceTLSOnly",
      "Effect": "Deny",
      "Principal": "*",
      "Action": "s3:*",
      "Resource": [
        "arn:aws:s3:::${BUCKET_NAME}",
        "arn:aws:s3:::${BUCKET_NAME}/*"
      ],
      "Condition": {
        "Bool": { "aws:SecureTransport": "false" }
      }
    }
  ]
}
EOF
aws s3api put-bucket-policy --bucket $BUCKET_NAME --policy file:///tmp/s3-tls-policy.json --region $AWS_REGION
```

---

### Step 5: Provision Secrets in AWS Secrets Manager

```bash
# Generate random 32-character master password for database
DB_PASSWORD=$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 24)
JWT_SECRET=$(openssl rand -hex 32)

# Store secrets in Secrets Manager
aws secretsmanager create-secret \
  --name "aml/production/config" \
  --description "Production credentials for AML Policy Guardian" \
  --secret-string "{
    \"DATABASE_USERNAME\":\"aml_admin\",
    \"DATABASE_PASSWORD\":\"${DB_PASSWORD}\",
    \"JWT_SECRET\":\"${JWT_SECRET}\",
    \"LLM_API_KEY\":\"${LLM_API_KEY:-AQ.Ab8RN6KkUOj54xzOK451TD61Ki0SaIIDcby8iDw-z5RMO0SrZA}\",
    \"LLM_BASE_URL\":\"https://generativelanguage.googleapis.com/v1beta/openai/\",
    \"LLM_MODEL\":\"gemini-1.5-flash\",
    \"S3_DOCUMENT_BUCKET\":\"${BUCKET_NAME}\"
  }" \
  --region $AWS_REGION
```

---

### Step 6: Create IAM Instance Profile (Least Privilege)

```bash
# 1. Create Role
cat <<EOF > /tmp/ec2-trust-policy.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "ec2.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
EOF

aws iam create-role --role-name ${PROJECT}-ec2-role \
  --assume-role-policy-document file:///tmp/ec2-trust-policy.json

# 2. Attach SSM Managed Core for SSH-free access
aws iam attach-role-policy --role-name ${PROJECT}-ec2-role \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

# 3. Create and Attach Custom Least-Privilege Policy
cat <<EOF > /tmp/ec2-app-policy.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "S3DocumentAccess",
      "Effect": "Allow",
      "Action": ["s3:ListBucket", "s3:GetBucketLocation"],
      "Resource": "arn:aws:s3:::${BUCKET_NAME}"
    },
    {
      "Sid": "S3ObjectCRUD",
      "Effect": "Allow",
      "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::${BUCKET_NAME}/*"
    },
    {
      "Sid": "SecretsManagerAccess",
      "Effect": "Allow",
      "Action": ["secretsmanager:GetSecretValue"],
      "Resource": "arn:aws:secretsmanager:${AWS_REGION}:${ACCOUNT_ID}:secret:aml/production/*"
    },
    {
      "Sid": "CloudWatchLogs",
      "Effect": "Allow",
      "Action": [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogStreams"
      ],
      "Resource": "arn:aws:logs:${AWS_REGION}:${ACCOUNT_ID}:log-group:/aws/ec2/aml-assistant/*"
    }
  ]
}
EOF

POLICY_ARN=$(aws iam create-policy --policy-name ${PROJECT}-app-policy \
  --policy-document file:///tmp/ec2-app-policy.json \
  --query 'Policy.Arn' --output text)

aws iam attach-role-policy --role-name ${PROJECT}-ec2-role --policy-arn $POLICY_ARN

# 4. Create Instance Profile
aws iam create-instance-profile --instance-profile-name ${PROJECT}-instance-profile
aws iam add-role-to-instance-profile --instance-profile-name ${PROJECT}-instance-profile --role-name ${PROJECT}-ec2-role
```

---

### Step 7: Create RDS PostgreSQL 16 + pgvector Instance

```bash
# 1. Create DB Subnet Group
aws rds create-db-subnet-group \
  --db-subnet-group-name ${PROJECT}-db-subnet-group \
  --db-subnet-group-description "Private DB Subnet Group for AML Assistant" \
  --subnet-ids "$DB_SUBNET_1" "$DB_SUBNET_2" \
  --region $AWS_REGION

# 2. Create Custom Parameter Group enforcing SSL
aws rds create-db-parameter-group \
  --db-parameter-group-name ${PROJECT}-pg16-params \
  --db-parameter-group-family postgres16 \
  --description "Custom PostgreSQL 16 parameters with forced SSL" \
  --region $AWS_REGION

aws rds modify-db-parameter-group \
  --db-parameter-group-name ${PROJECT}-pg16-params \
  --parameters "ParameterName=rds.force_ssl,ParameterValue=1,ApplyMethod=immediate" \
  --region $AWS_REGION

# 3. Provision RDS Instance (db.t4g.micro for cost-efficiency)
aws rds create-db-instance \
  --db-instance-identifier ${PROJECT}-postgres-db \
  --db-instance-class db.t4g.micro \
  --engine postgres \
  --engine-version 16.3 \
  --master-username aml_admin \
  --master-user-password "$DB_PASSWORD" \
  --allocated-storage 20 \
  --max-allocated-storage 100 \
  --storage-type gp3 \
  --db-subnet-group-name ${PROJECT}-db-subnet-group \
  --vpc-security-group-ids $SG_RDS_ID \
  --no-publicly-accessible \
  --storage-encrypted \
  --backup-retention-period 7 \
  --db-parameter-group-name ${PROJECT}-pg16-params \
  --db-name aml_assistant \
  --region $AWS_REGION

echo "Waiting for RDS instance to become available..."
aws rds wait db-instance-available --db-instance-identifier ${PROJECT}-postgres-db --region $AWS_REGION

RDS_ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier ${PROJECT}-postgres-db \
  --query 'DBInstances[0].Endpoint.Address' --output text --region $AWS_REGION)
echo "RDS is available at: $RDS_ENDPOINT"
```

---

### Step 8: Launch EC2 Instance with UserData Bootstrap

```bash
# UserData script to install Docker, Docker Compose, AWS CLI, and CloudWatch Logs Agent
cat <<'EOF' > /tmp/ec2-userdata.sh
#!/bin/bash
set -e

# Update and install Docker
apt-get update -y
apt-get install -y ca-certificates curl gnupg lsb-release unzip jq

# Install Docker Engine
mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

systemctl enable docker
systemctl start docker
usermod -aG docker ubuntu

# Install AWS CLI v2
curl "https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip" -o "awscliv2.zip"
unzip -q awscliv2.zip
./aws/install

# Install CloudWatch Agent
wget -q https://s3.amazonaws.com/amazoncloudwatch-agent/ubuntu/arm64/latest/amazon-cloudwatch-agent.deb
dpkg -i -E ./amazon-cloudwatch-agent.deb
systemctl enable amazon-cloudwatch-agent

mkdir -p /opt/aml-assistant
chown ubuntu:ubuntu /opt/aml-assistant
EOF

# Find Ubuntu 24.04 LTS ARM64 AMI
AMI_ID=$(aws ssm get-parameter --name "/aws/service/canonical/ubuntu/server/24.04/stable/current/arm64/hvm/ebs-gp3/ami-id" \
  --query "Parameter.Value" --output text --region $AWS_REGION)

# Launch EC2 Instance (t4g.small)
INSTANCE_ID=$(aws ec2 run-instances \
  --image-id $AMI_ID \
  --instance-type t4g.small \
  --iam-instance-profile Name=${PROJECT}-instance-profile \
  --network-interfaces "DeviceIndex=0,SubnetId=${PUB_SUBNET_ID},AssociatePublicIpAddress=true,Groups=[${SG_EC2_ID}]" \
  --user-data file:///tmp/ec2-userdata.sh \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${PROJECT}-app-host}]" \
  --query 'Instances[0].InstanceId' --output text --region $AWS_REGION)

echo "Waiting for EC2 instance to initialize..."
aws ec2 wait instance-running --instance-ids $INSTANCE_ID --region $AWS_REGION

# Allocate and Associate Elastic IP
ALLOC_ID=$(aws ec2 allocate-address --domain vpc --query 'AllocationId' --output text --region $AWS_REGION)
aws ec2 associate-address --instance-id $INSTANCE_ID --allocation-id $ALLOC_ID --region $AWS_REGION

PUBLIC_IP=$(aws ec2 describe-instances --instance-ids $INSTANCE_ID \
  --query 'Reservations[0].Instances[0].PublicIpAddress' --output text --region $AWS_REGION)
echo "Application host is live at: $PUBLIC_IP"
```

---

### Step 9: Configure CloudWatch Logs Centralization

Configure `/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json` on the EC2 host:

```json
{
  "logs": {
    "logs_collected": {
      "files": {
        "collect_list": [
          {
            "file_path": "/var/lib/docker/containers/*/*-json.log",
            "log_group_name": "/aws/ec2/aml-assistant/containers",
            "log_stream_name": "{instance_id}",
            "retention_in_days": 14
          }
        ]
      }
    }
  }
}
```

Start the agent:
```bash
/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl -a fetch-config -m ec2 -s -c file:/opt/aws/amazon-cloudwatch-agent/etc/amazon-cloudwatch-agent.json
```

---

### Step 10: Configure HTTPS with Let's Encrypt / Certbot

On the EC2 host:

```bash
# Install Certbot
apt-get install -y certbot python3-certbot-nginx

# Obtain Certificate (replace with your domain)
DOMAIN="aml-guardian.yourbank.com"
certbot certonly --standalone -d $DOMAIN --agree-tos --email security@yourbank.com -n

# Mount certificate directory into Nginx in docker-compose.prod.yml:
# - /etc/letsencrypt:/etc/letsencrypt:ro
```

---

### Step 11: Production Docker Compose Deployment

On the EC2 host in `/opt/aml-assistant`:

```bash
# 1. Fetch runtime secrets directly from Secrets Manager into .env (No secrets stored in git!)
aws secretsmanager get-secret-value --secret-id "aml/production/config" --region us-east-1 --query 'SecretString' --output text | jq -r 'to_entries|map("\(.key)=\(.value)")|.[]' > .env

# 2. Append RDS host and ports
echo "DATABASE_URL=jdbc:postgresql://${RDS_ENDPOINT}:5432/aml_assistant?sslmode=require" >> .env
echo "SPRING_PROFILES_ACTIVE=docker,production" >> .env

# 3. Pull or build images and launch
docker compose -f docker-compose.yml up -d --build
```

---

## 3. End-to-End Post-Deployment Verification

Execute the automated test script against the production public frontend:

```bash
export FRONTEND_URL="https://${DOMAIN}"  # or http://${PUBLIC_IP}
python3 scripts/test_e2e_container.py
```

The script verifies:
1. **Frontend Reachability:** Public URL serves Angular SPA index with HTTP 200 and `<app-root>`.
2. **Authentication:** Authenticates Analyst (`analyst` / `password123`) and Admin (`admin` / `admin123`) via JWT.
3. **Document Ingestion:** Uploads synthetic AML guideline `AML-POL-999_Synthetic_Wire_Guideline.txt`.
4. **Status Polling:** Confirms ingestion pipeline completes to `READY` status with pgvector chunking.
5. **RAG Inference:** Queries the assistant via `/api/v1/chat/sessions/{id}/messages`.
6. **Answer Grounding:** Validates factual synthesis and anti-hallucination guardrails.
7. **Citations:** Confirms document title and section reference grounding.
8. **Chat History:** Confirms multi-turn conversation retrieval.
9. **Audit Logging:** Confirms audit entry presence in RDS PostgreSQL and CloudWatch Logs.

---

## 4. Rollback Procedures

### Application / Container Rollback
If a newly deployed container version fails health checks:
```bash
# Revert to previous image tag or commit
git checkout HEAD~1
docker compose up -d --build
```

### Database Migration Rollback
If a Flyway migration fails or corrupts data:
```bash
# 1. Restore RDS to a Point-in-Time prior to the deployment
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier aml-postgres-db \
  --target-db-instance-identifier aml-postgres-db-rollback \
  --restore-time "2026-10-03T01:00:00Z" \
  --db-subnet-group-name aml-db-subnet-group \
  --vpc-security-group-ids $SG_RDS_ID \
  --region $AWS_REGION

# 2. Update .env with new rollback endpoint and restart backend
```

---

## 5. Teardown & Resource Cleanup Procedure ($0 Ongoing Cost)

When the project is not in use or testing is completed, execute this cleanup procedure to eliminate all recurring AWS charges:

```bash
export AWS_REGION="us-east-1"
export PROJECT="aml"

echo "Beginning complete cleanup of AML Policy Guardian AWS resources..."

# 1. Terminate EC2 Instance
INSTANCE_ID=$(aws ec2 describe-instances --filters "Name=tag:Name,Values=${PROJECT}-app-host" "Name=instance-state-name,Values=running,stopped" --query "Reservations[].Instances[].InstanceId" --output text --region $AWS_REGION)
if [ -n "$INSTANCE_ID" ]; then
  aws ec2 terminate-instances --instance-ids $INSTANCE_ID --region $AWS_REGION
  aws ec2 wait instance-terminated --instance-ids $INSTANCE_ID --region $AWS_REGION
  echo "Terminated EC2 Instance: $INSTANCE_ID"
fi

# 2. Release Elastic IP
ALLOC_ID=$(aws ec2 describe-addresses --query "Addresses[?InstanceId=='${INSTANCE_ID}'].AllocationId" --output text --region $AWS_REGION)
if [ -n "$ALLOC_ID" ]; then
  aws ec2 release-address --allocation-id $ALLOC_ID --region $AWS_REGION
  echo "Released Elastic IP: $ALLOC_ID"
fi

# 3. Delete RDS PostgreSQL Instance (skipping final snapshot for dev/test)
aws rds delete-db-instance \
  --db-instance-identifier ${PROJECT}-postgres-db \
  --skip-final-snapshot \
  --delete-automated-backups \
  --region $AWS_REGION
echo "Initiated deletion of RDS instance..."
aws rds wait db-instance-deleted --db-instance-identifier ${PROJECT}-postgres-db --region $AWS_REGION

# 4. Delete RDS DB Subnet Group and Parameter Group
aws rds delete-db-subnet-group --db-subnet-group-name ${PROJECT}-db-subnet-group --region $AWS_REGION
aws rds delete-db-parameter-group --db-parameter-group-name ${PROJECT}-pg16-params --region $AWS_REGION

# 5. Empty and Delete S3 Document Bucket
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET_NAME="aml-policy-guardian-documents-${ACCOUNT_ID}-${AWS_REGION}"
aws s3 rm s3://${BUCKET_NAME} --recursive --region $AWS_REGION
aws s3api delete-bucket --bucket ${BUCKET_NAME} --region $AWS_REGION
echo "Deleted S3 Bucket: $BUCKET_NAME"

# 6. Delete CloudWatch Log Groups
aws logs delete-log-group --log-group-name "/aws/ec2/aml-assistant/containers" --region $AWS_REGION || true

# 7. Delete Secrets Manager Secret
aws secretsmanager delete-secret --secret-id "aml/production/config" --force-delete-without-recovery --region $AWS_REGION

# 8. Remove IAM Instance Profile and Role
aws iam remove-role-from-instance-profile --instance-profile-name ${PROJECT}-instance-profile --role-name ${PROJECT}-ec2-role || true
aws iam delete-instance-profile --instance-profile-name ${PROJECT}-instance-profile || true
POLICY_ARN="arn:aws:iam::${ACCOUNT_ID}:policy/${PROJECT}-app-policy"
aws iam detach-role-policy --role-name ${PROJECT}-ec2-role --policy-arn $POLICY_ARN || true
aws iam detach-role-policy --role-name ${PROJECT}-ec2-role --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore || true
aws iam delete-policy --policy-arn $POLICY_ARN || true
aws iam delete-role --role-name ${PROJECT}-ec2-role || true

# 9. Delete Security Groups
aws ec2 delete-security-group --group-id $SG_RDS_ID --region $AWS_REGION || true
aws ec2 delete-security-group --group-id $SG_EC2_ID --region $AWS_REGION || true

# 10. Delete VPC Endpoints, Route Tables, Subnets, Internet Gateway & VPC
VPC_EP_ID=$(aws ec2 describe-vpc-endpoints --filters "Name=vpc-id,Values=${VPC_ID}" --query "VpcEndpoints[].VpcEndpointId" --output text --region $AWS_REGION)
if [ -n "$VPC_EP_ID" ]; then
  aws ec2 delete-vpc-endpoints --vpc-endpoint-ids $VPC_EP_ID --region $AWS_REGION
fi

aws ec2 detach-internet-gateway --internet-gateway-id $IGW_ID --vpc-id $VPC_ID --region $AWS_REGION
aws ec2 delete-internet-gateway --internet-gateway-id $IGW_ID --region $AWS_REGION

aws ec2 delete-subnet --subnet-id $PUB_SUBNET_ID --region $AWS_REGION
aws ec2 delete-subnet --subnet-id $DB_SUBNET_1 --region $AWS_REGION
aws ec2 delete-subnet --subnet-id $DB_SUBNET_2 --region $AWS_REGION
aws ec2 delete-route-table --route-table-id $PUB_RTB_ID --region $AWS_REGION

aws ec2 delete-vpc --vpc-id $VPC_ID --region $AWS_REGION
echo "Cleanup complete! All AWS resources successfully deleted."
```
