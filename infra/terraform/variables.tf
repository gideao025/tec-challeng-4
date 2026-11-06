variable "proxmox_endpoint" {
  description = "URL da API do Proxmox, ex.: https://pve.lan:8006/"
  type        = string
}

variable "proxmox_api_token" {
  description = "Token da API no formato usuario@realm!id=segredo (use TF_VAR_proxmox_api_token)"
  type        = string
  sensitive   = true
}

variable "proxmox_insecure" {
  description = "Aceitar certificado autoassinado do Proxmox"
  type        = bool
  default     = true
}

variable "proxmox_ssh_user" {
  type    = string
  default = "root"
}

variable "node_name" {
  description = "Nó do Proxmox onde a VM será criada"
  type        = string
  default     = "pve"
}

variable "template_vm_id" {
  description = "ID do template Ubuntu 24.04 cloud-init (ver README)"
  type        = number
  default     = 9000
}

variable "vm_id" {
  type    = number
  default = 400
}

variable "datastore_id" {
  description = "Datastore dos discos da VM"
  type        = string
  default     = "local-lvm"
}

variable "snippets_datastore_id" {
  description = "Datastore com conteúdo 'snippets' habilitado"
  type        = string
  default     = "local"
}

variable "bridge" {
  type    = string
  default = "vmbr0"
}

variable "ipv4_cidr" {
  description = "IP fixo da VM (ex.: 192.168.1.50/24) ou \"dhcp\""
  type        = string
  default     = "dhcp"
}

variable "ipv4_gateway" {
  type    = string
  default = null
}

variable "dns_servers" {
  type    = list(string)
  default = ["1.1.1.1", "8.8.8.8"]
}

variable "disk_gb" {
  type    = number
  default = 40
}

variable "vm_user" {
  type    = string
  default = "fiap"
}

variable "ssh_public_keys" {
  description = "Chaves públicas SSH autorizadas para o usuário da VM"
  type        = list(string)
}

variable "repo_url" {
  description = "URL git do repositório do grupo, clonado em /opt/feedback"
  type        = string
}
