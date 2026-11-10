variable "aws_region" {
  type        = string
  description = "Target AWS deployment region"
  default     = "us-east-1"
}

variable "environment" {
  type        = string
  description = "Environment identifier (e.g., prod, staging)"
  default     = "prod"
}

variable "vpc_cidr" {
  type        = string
  description = "VPC CIDR block"
  default     = "10.0.0.0/16"
}

variable "db_instance_class" {
  type        = string
  description = "RDS instance size for PostgreSQL + pgvector"
  default     = "db.t4g.medium"
}

variable "db_allocated_storage" {
  type        = number
  description = "Allocated storage in GB"
  default     = 50
}

variable "db_name" {
  type        = string
  description = "Database name"
  default     = "aml_assistant"
}

variable "db_username" {
  type        = string
  description = "Master database username"
  default     = "amladmin"
}

variable "db_password" {
  type        = string
  description = "Master database password"
  sensitive   = true
}

variable "redis_node_type" {
  type        = string
  description = "ElastiCache Redis node type"
  default     = "cache.t4g.small"
}

variable "eks_node_instance_types" {
  type        = list(string)
  description = "EC2 instance types for EKS worker nodes"
  default     = ["t3a.xlarge"]
}

variable "eks_desired_capacity" {
  type        = number
  description = "Desired number of EKS worker nodes"
  default     = 3
}
