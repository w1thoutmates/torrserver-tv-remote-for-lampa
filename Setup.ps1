$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
try {
    if (!(Test-Path -LiteralPath (Join-Path $PSScriptRoot 'TorrServer-matrix-windows-amd64.exe'))) {
        throw 'Скачайте TorrServer-matrix-windows-amd64.exe и положите рядом с Setup.cmd.'
    }
    if (Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object LocalPort -in 8090,8091) {
        throw 'Порт 8090 или 8091 уже занят. Остановите предыдущую установку перед настройкой.'
    }
    $form = New-Object Windows.Forms.Form
    $form.Text = 'Настройка TorrServer MatriX'
    $form.ClientSize = New-Object Drawing.Size(560,145)
    $form.StartPosition = 'CenterScreen'
    $form.FormBorderStyle = 'FixedDialog'
    $label = New-Object Windows.Forms.Label
    $label.Text = 'Выберите частную сеть ПК, к которой подключён телевизор.'
    $label.SetBounds(14,15,528,25)
    $box = New-Object Windows.Forms.ComboBox
    $box.DropDownStyle = 'DropDownList'
    $box.SetBounds(14,45,530,32)
    $choices = @(foreach ($profile in Get-NetConnectionProfile | Where-Object NetworkCategory -eq Private) {
        Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $profile.InterfaceIndex -ErrorAction SilentlyContinue |
            Where-Object { $_.AddressState -eq 'Preferred' -and $_.PrefixLength -in (1..32) }
    })
    if (!$choices.Count) { throw 'Нет доступного IPv4 в сети с профилем «Частная». Проверьте профиль сети Windows.' }
    foreach ($address in $choices) { [void]$box.Items.Add(('{0} — {1}/{2}' -f $address.InterfaceAlias,$address.IPAddress,$address.PrefixLength)) }
    $box.SelectedIndex = 0
    $button = New-Object Windows.Forms.Button
    $button.Text = 'Установить'
    $button.SetBounds(395,90,150,32)
    $button.DialogResult = 'OK'
    $form.AcceptButton = $button
    $form.Controls.AddRange(@($label,$box,$button))
    if ($form.ShowDialog() -ne 'OK') { return }
    $choice = $choices[$box.SelectedIndex]
    & (Join-Path $PSScriptRoot 'Build.ps1')
    $xml = [xml]'<Network><Address/><Prefix/><Interface/></Network>'
    $xml.Network.Address = [string]$choice.IPAddress
    $xml.Network.Prefix = [string]$choice.PrefixLength
    $xml.Network.Interface = [string]$choice.InterfaceAlias
    $xml.Save((Join-Path $PSScriptRoot 'network.xml'))
    & (Join-Path $PSScriptRoot 'Manage.ps1') Install
    $address = $choice.IPAddress
    [void][Windows.Forms.MessageBox]::Show("Управление с ТВ: http://${address}:8091/
Адрес для Lampa: http://${address}:8090/",'Готово')
} catch {
    [void][Windows.Forms.MessageBox]::Show($_.Exception.Message,'Ошибка настройки')
    exit 1
}
