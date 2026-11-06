output "cloudlab_ipv4" {
  description = "IPs reportados pelo qemu-guest-agent"
  value       = proxmox_virtual_environment_vm.cloudlab.ipv4_addresses
}

output "proximo_passo" {
  value = "ssh ${var.vm_user}@<ip>  &&  cd /opt/feedback && make up && make deploy"
}
