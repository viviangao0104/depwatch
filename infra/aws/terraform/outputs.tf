output "ec2_public_ip" {
  value = aws_eip.app.public_ip
}

output "cloudfront_domain" {
  value = aws_cloudfront_distribution.main.domain_name
}

output "cloudfront_id" {
  value = aws_cloudfront_distribution.main.id
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "manifest_bucket" {
  value = aws_s3_bucket.manifests.bucket
}

output "ecr_registry" {
  value = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.region}.amazonaws.com"
}

output "github_actions_role_arn" {
  value = var.github_repo == "" ? null : aws_iam_role.github_actions[0].arn
}

output "instance_id" {
  value = aws_instance.app.id
}
