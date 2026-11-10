variable "region" {
  type    = string
  default = "us-east-1"
}

variable "instance_type" {
  description = "arm64 Graviton is ~20% cheaper and AL2023 has arm64 AMIs; Kafka + Postgres + 4 JVMs need at least 4 GB RAM"
  type        = string
  default     = "t4g.medium"
}

variable "key_pair_name" {
  description = "Name of an EC2 Key Pair already created in the AWS console"
  type        = string
}

variable "my_ip_cidr" {
  description = "IP allowed to SSH, e.g. 1.2.3.4/32 (check with: curl ifconfig.me)"
  type        = string
}

variable "github_repo" {
  description = "owner/repo for GitHub Actions OIDC; leave empty to skip"
  type        = string
  default     = ""
}
