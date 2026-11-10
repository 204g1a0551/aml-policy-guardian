# RDS PostgreSQL 16 with pgvector extension support
resource "aws_db_subnet_group" "db" {
  name       = "aml-${var.environment}-db-subnet-group"
  subnet_ids = aws_subnet.private_db[*].id

  tags = {
    Name = "aml-${var.environment}-db-subnet-group"
  }
}

resource "aws_security_group" "rds" {
  name        = "aml-${var.environment}-rds-sg"
  description = "Allows PostgreSQL inbound traffic strictly from EKS worker nodes"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "PostgreSQL from Private Application Subnets"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    cidr_blocks     = aws_subnet.private_app[*].cidr_block
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "aml-${var.environment}-rds-sg"
  }
}

resource "aws_db_parameter_group" "pgvector" {
  name   = "aml-${var.environment}-pg16-pgvector"
  family = "postgres16"

  parameter {
    name  = "shared_preload_libraries"
    value = "vector"
  }

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  tags = {
    Name = "aml-${var.environment}-pgvector-params"
  }
}

resource "aws_db_instance" "postgres" {
  identifier             = "aml-${var.environment}-postgres"
  engine                 = "postgres"
  engine_version         = "16.1"
  instance_class         = var.db_instance_class
  allocated_storage      = var.db_allocated_storage
  max_allocated_storage  = 200
  storage_type           = "gp3"
  storage_encrypted      = true
  db_name                = var.db_name
  username               = var.db_username
  password               = var.db_password
  db_subnet_group_name   = aws_db_subnet_group.db.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.pgvector.name
  multi_az               = var.environment == "prod" ? true : false
  skip_final_snapshot    = var.environment != "prod"
  deletion_protection    = var.environment == "prod" ? true : false
  backup_retention_period = 30

  tags = {
    Name = "aml-${var.environment}-postgres"
  }
}
