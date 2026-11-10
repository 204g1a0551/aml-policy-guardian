# Redis Cluster on ElastiCache for Distributed Retrieval Caching
resource "aws_elasticache_subnet_group" "redis" {
  name       = "aml-${var.environment}-redis-subnet-group"
  subnet_ids = aws_subnet.private_db[*].id
}

resource "aws_security_group" "redis" {
  name        = "aml-${var.environment}-redis-sg"
  description = "Allows Redis access strictly from EKS worker nodes"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "Redis from Private Application Subnets"
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = aws_subnet.private_app[*].cidr_block
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "aml-${var.environment}-redis-sg"
  }
}

resource "aws_elasticache_cluster" "redis" {
  cluster_id           = "aml-${var.environment}-redis"
  engine               = "redis"
  node_type            = var.redis_node_type
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379
  subnet_group_name    = aws_elasticache_subnet_group.redis.name
  security_group_ids   = [aws_security_group.redis.id]

  tags = {
    Name = "aml-${var.environment}-redis"
  }
}
