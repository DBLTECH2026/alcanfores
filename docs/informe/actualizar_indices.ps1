# Actualiza el índice general (campo TOC), el índice de tablas y figuras (campos PAGEREF) y exporta el PDF del informe.
# Uso:  powershell -ExecutionPolicy Bypass -File actualizar_indices.ps1 [-Docx "ruta\informe.docx"]
# Abre SOLO este documento, lo cierra al terminar y cierra Word únicamente si no quedó ningún otro documento abierto
# (el usuario puede tener Word abierto con cambios sin guardar).
param(
    [string]$Docx = "C:\Users\Luis Lopez Lapa\Desktop\ALCANFORES\02 DESARROLLO WEB INTEGRADO (Spring Boot + React)\04 Entrega final\01 Informe final\INFORME_DESARROLLO_WEB_FINAL.docx"
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path $Docx)) { throw "No existe $Docx" }
$Docx = (Resolve-Path $Docx).Path
$Pdf = [System.IO.Path]::ChangeExtension($Docx, ".pdf")

$word = New-Object -ComObject Word.Application
$alertasPrevias = $word.DisplayAlerts
$word.DisplayAlerts = 0
$doc = $null
try {
    # Open(FileName, ConfirmConversions, ReadOnly, AddToRecentFiles)
    $doc = $word.Documents.Open($Docx, $false, $false, $false)
    $doc.Repaginate()
    for ($i = 1; $i -le $doc.TablesOfContents.Count; $i++) { $doc.TablesOfContents.Item($i).Update() }
    [void]$doc.Fields.Update()                  # PAGEREF del índice de tablas y figuras
    $doc.Repaginate()
    for ($i = 1; $i -le $doc.TablesOfContents.Count; $i++) { $doc.TablesOfContents.Item($i).UpdatePageNumbers() }
    [void]$doc.Fields.Update()
    $doc.Save()
    $paginas = $doc.ComputeStatistics(2)       # wdStatisticPages
    # ExportAsFixedFormat(OutputFileName, ExportFormat=17 PDF, OpenAfterExport, OptimizeFor=0 impresión,
    #                     Range=0 todo, From, To, Item=0 contenido, IncludeDocProps, KeepIRM, CreateBookmarks=1 títulos)
    $doc.ExportAsFixedFormat($Pdf, 17, $false, 0, 0, 1, 1, 0, $true, $true, 1)
    Write-Output "Páginas: $paginas"
    Write-Output "PDF: $Pdf"
}
finally {
    if ($doc -ne $null) { $doc.Close(0) ; [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($doc) }
    $word.DisplayAlerts = $alertasPrevias
    if ($word.Documents.Count -eq 0) { $word.Quit() }
    [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($word)
}
