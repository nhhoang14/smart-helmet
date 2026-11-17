param([string]$LocalAddress = '10.72.117.44', [string]$RemoteSubnet = '10.72.117.0/24')
$ErrorActionPreference = 'Stop'
$ruleName = 'SmartHelmet-MQTT-LAN-1883'
if (-not (Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -Name $ruleName -DisplayName 'Smart Helmet MQTT local LAN 1883' `
        -Direction Inbound -Action Allow -Protocol TCP -LocalPort 1883 `
        -LocalAddress $LocalAddress -RemoteAddress $RemoteSubnet -Profile Any -ErrorAction Stop | Out-Null
} else {
    Get-NetFirewallRule -Name $ruleName | Get-NetFirewallAddressFilter | Set-NetFirewallAddressFilter `
        -LocalAddress $LocalAddress -RemoteAddress $RemoteSubnet -ErrorAction Stop | Out-Null
    Enable-NetFirewallRule -Name $ruleName
}
Write-Host "MQTT inbound enabled for $RemoteSubnet to ${LocalAddress}:1883."
