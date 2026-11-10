output "vpc_id" {
  description = "ID of the AML VPC"
  value       = aws_vpc.main.id
}

output "eks_cluster_endpoint" {
  description = "Endpoint for Amazon EKS control plane"
  value       = aws_eks_cluster.main.endpoint
}

output "eks_cluster_name" {
  description = "Name of the EKS Cluster"
  value       = aws_eks_cluster.main.name
}

output "rds_endpoint" {
  description = "Connection endpoint for PostgreSQL + pgvector"
  value       = aws_db_instance.postgres.endpoint
}

output "redis_endpoint" {
  description = "Primary endpoint for ElastiCache Redis"
  value       = aws_elasticache_cluster.redis.cache_nodes[0].address
}
