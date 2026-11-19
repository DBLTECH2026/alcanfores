# Conecta el «cerebro» de Canchita (la IA de Alcanfores) con la suscripción de Claude de ESTA PC.
# 1) «claude setup-token» genera un token de larga duración (sk-ant-oat01-…, dura 1 año).
# 2) Se pega aquí y viaja directo al servidor de Alcanfores por SSH; no se muestra ni se guarda en esta PC.
# Se lanza con doble clic desde «CONECTAR CANCHITA.bat» (escritorio).
$Host.UI.RawUI.WindowTitle = 'Conectar Canchita'
$llave = Join-Path $HOME '.ssh\alcanfores_actions'
$vps = 'sirac@2.25.216.93'
$ok = $false

Write-Host ''
Write-Host '  ==============================================' -ForegroundColor Green
Write-Host '   CONECTAR EL CEREBRO DE CANCHITA (Alcanfores)' -ForegroundColor Green
Write-Host '  ==============================================' -ForegroundColor Green
Write-Host ''
Write-Host '  Paso 1: se abrirá el navegador. Autoriza con tu cuenta de Claude.' -ForegroundColor Cyan
Write-Host '  Al final aparece aquí un texto largo que empieza con  sk-ant-oat01-' -ForegroundColor Cyan
Write-Host '  Selecciónalo con el mouse y cópialo (Enter o clic derecho copia).' -ForegroundColor Cyan
Write-Host ''
claude setup-token

for ($i = 1; $i -le 3 -and -not $ok; $i++) {
  Write-Host ''
  $t = Read-Host '  Paso 2: pega aquí el token (clic derecho = pegar) y presiona Enter'
  $t = ($t -replace '\s', '')
  if ($t.StartsWith('CLAUDE_CODE_OAUTH_TOKEN=')) { $t = $t.Substring(24) }
  if ($t -notmatch '^sk-ant-oat\S{60,}$') {
    Write-Host '  Eso no parece el token completo (empieza con sk-ant-oat y es largo). Intenta de nuevo.' -ForegroundColor Yellow
    continue
  }
  $t | ssh -i $llave -o ConnectTimeout=15 $vps 'bash ~/poner-llave-ia.sh'
  if ($LASTEXITCODE -eq 0) { $ok = $true }
}

Write-Host ''
if ($ok) {
  Write-Host '  LISTO: Canchita ya tiene cerebro. Puedes cerrar esta ventana e irte a dormir.' -ForegroundColor Green
} else {
  Write-Host '  No se pudo guardar el token. No pasa nada: mañana lo vemos juntos.' -ForegroundColor Red
}
Write-Host ''
Read-Host '  Presiona Enter para cerrar'
