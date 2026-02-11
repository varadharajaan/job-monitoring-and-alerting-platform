# ============================================================================
# Outputs — used to configure the platform services
# ============================================================================

output "vpc_id" {
  value = module.vpc.vpc_id
}

output "eks_cluster_name" {
  value = module.eks.cluster_name
}

output "eks_cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "rds_endpoint" {
  value = module.rds.endpoint
}

output "rds_connection_url" {
  value     = "jdbc:postgresql://${module.rds.endpoint}:5432/${var.db_name}?sslmode=require"
  sensitive = true
}

output "redis_endpoint" {
  value = module.redis.primary_endpoint
}

output "msk_bootstrap_brokers_tls" {
  value = module.msk.bootstrap_brokers_tls
}

output "opensearch_endpoint" {
  value = module.opensearch.endpoint
}

output "ecr_repository_urls" {
  value = module.ecr.repository_urls
}

output "s3_bucket_name" {
  value = module.s3.bucket_name
}

output "alb_dns_name" {
  value = module.alb.dns_name
}

output "kubeconfig_command" {
  value = "aws eks update-kubeconfig --name ${module.eks.cluster_name} --region ${var.aws_region}"
}
