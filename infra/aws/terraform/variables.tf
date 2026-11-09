variable "region" {
  type    = string
  default = "us-east-1"
}

variable "instance_type" {
  description = "arm64 Graviton 实例便宜 20% 且 AL2023 arm64 AMI 可用；Kafka+Postgres+4 个 JVM 至少 4GB 内存"
  type        = string
  default     = "t4g.medium"
}

variable "key_pair_name" {
  description = "已在 AWS 控制台创建的 EC2 Key Pair 名称"
  type        = string
}

variable "my_ip_cidr" {
  description = "允许 SSH 的 IP，形如 1.2.3.4/32（curl ifconfig.me 查看）"
  type        = string
}

variable "github_repo" {
  description = "owner/repo，用于 GitHub Actions OIDC；留空则不创建"
  type        = string
  default     = ""
}
