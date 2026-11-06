# VM "cloudlab": 4 vCPU, 6 GB RAM, Ubuntu 24.04, clonada de um template cloud-init.
resource "proxmox_virtual_environment_file" "cloud_init" {
  content_type = "snippets"
  datastore_id = var.snippets_datastore_id
  node_name    = var.node_name

  source_raw {
    file_name = "cloudlab-user-data.yaml"
    data = templatefile("${path.module}/cloud-init.yaml.tftpl", {
      usuario         = var.vm_user
      ssh_public_keys = var.ssh_public_keys
      repo_url        = var.repo_url
      script_instalar = file("${path.module}/../../scripts/instalar-cloudlab.sh")
    })
  }
}

resource "proxmox_virtual_environment_vm" "cloudlab" {
  name        = "cloudlab"
  description = "FIAP Tech Challenge Fase 4 - nuvem privada (faasd + serviços de apoio)"
  tags        = ["fiap", "tech-challenge", "faasd"]
  node_name   = var.node_name
  vm_id       = var.vm_id

  clone {
    vm_id = var.template_vm_id
    full  = true
  }

  agent {
    enabled = true
  }

  cpu {
    cores = 4
    type  = "host"
  }

  memory {
    dedicated = 6144
  }

  disk {
    datastore_id = var.datastore_id
    interface    = "scsi0"
    size         = var.disk_gb
    discard      = "on"
    ssd          = true
  }

  network_device {
    bridge = var.bridge
  }

  operating_system {
    type = "l26"
  }

  initialization {
    datastore_id = var.datastore_id

    ip_config {
      ipv4 {
        address = var.ipv4_cidr
        gateway = var.ipv4_cidr == "dhcp" ? null : var.ipv4_gateway
      }
    }

    dns {
      servers = var.dns_servers
    }

    user_data_file_id = proxmox_virtual_environment_file.cloud_init.id
  }
}
