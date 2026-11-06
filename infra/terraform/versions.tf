terraform {
  required_version = ">= 1.6"
  required_providers {
    proxmox = {
      source  = "bpg/proxmox"
      version = "~> 0.120"
    }
  }
}

provider "proxmox" {
  endpoint  = var.proxmox_endpoint
  api_token = var.proxmox_api_token
  insecure  = var.proxmox_insecure

  # O upload de snippets (cloud-init) é feito via SSH no nó Proxmox.
  ssh {
    agent    = true
    username = var.proxmox_ssh_user
  }
}
