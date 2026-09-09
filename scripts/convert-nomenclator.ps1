<#
.SYNOPSIS
    Convertor Nomenclator ANMDMR (xlsx) -> app/src/main/assets/nomenclator.tsv.gz

.DESCRIPTION
    Descarca fisierul public de pe nomenclator.anm.ro, il parseaza ca XML brut (xlsx e un
    arhiva zip cu xl/sharedStrings.xml + xl/worksheets/sheet1.xml) — NU foloseste nicio
    librarie externa de parsare xlsx (Apache POI etc. nu au sens pe un proiect Android/Kotlin;
    parsarea XML .NET nativa e suficienta si robusta pentru acest fisier).

    Ruleaza manual, o data, la fiecare actualizare reala a Nomenclatorului (dataset-ul se
    schimba rar — nu face parte din build-ul normal al aplicatiei).

    Sursa: https://nomenclator.anm.ro/medicamente (link descarcare: /files/nomenclator.xlsx)
#>

$ErrorActionPreference = "Stop"

$url = "https://nomenclator.anm.ro/files/nomenclator.xlsx"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$assetsDir = Join-Path $repoRoot "app\src\main\assets"
$outGz = Join-Path $assetsDir "nomenclator.tsv.gz"
$tmpXlsx = Join-Path $env:TEMP "nomenclator_download.xlsx"

Write-Host "Descarc $url ..."
Invoke-WebRequest -Uri $url -OutFile $tmpXlsx -UserAgent "Mozilla/5.0"

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($tmpXlsx)

function Get-XmlFromZipEntry($zipArchive, [string]$entryName) {
    $entry = $zipArchive.GetEntry($entryName)
    if (-not $entry) { throw "Nu gasesc $entryName in xlsx" }
    $stream = $entry.Open()
    $doc = New-Object System.Xml.XmlDocument
    $doc.Load($stream)
    $stream.Close()
    return $doc
}

Write-Host "Parsez sharedStrings.xml ..."
$sstDoc = Get-XmlFromZipEntry $zip "xl/sharedStrings.xml"
$ns = New-Object System.Xml.XmlNamespaceManager($sstDoc.NameTable)
$ns.AddNamespace("s", "http://schemas.openxmlformats.org/spreadsheetml/2006/main")

$siNodes = $sstDoc.SelectNodes("//s:si", $ns)
$sharedStrings = New-Object System.Collections.Generic.List[string]
foreach ($si in $siNodes) {
    $textNodes = $si.SelectNodes(".//s:t", $ns)
    $sb = New-Object System.Text.StringBuilder
    foreach ($t in $textNodes) { [void]$sb.Append($t.InnerText) }
    $sharedStrings.Add($sb.ToString())
}
Write-Host "  $($sharedStrings.Count) siruri unice"

Write-Host "Parsez sheet1.xml (fisier mare, poate dura cateva secunde) ..."
$sheetDoc = Get-XmlFromZipEntry $zip "xl/worksheets/sheet1.xml"
$zip.Dispose()

$rows = $sheetDoc.SelectNodes("//s:row", $ns)
Write-Host "  $($rows.Count) randuri (inclusiv antet)"

# Ordinea reala a coloanelor in nomenclator.xlsx (A..T), verificata din antet la inspectarea
# manuala a fisierului: Cod CIM, Denumire comerciala, DCI, Forma farmaceutica, Concentratie,
# Firma/tara producatoare APP, Firma/tara detinatoare APP, Cod ATC, Actiune terapeutica,
# Prescriptie, Nr/data ambalaj APP, Ambalaj, Volum ambalaj, Valabilitate ambalaj, Bulina, Diez,
# Stea, Triunghi, Dreptunghi, Data actualizare.
$colCount = 20

function Get-ColumnIndex([string]$cellRef) {
    $letters = ($cellRef -replace '[0-9]', '')
    $idx = 0
    foreach ($ch in $letters.ToCharArray()) {
        $idx = $idx * 26 + ([int][char]$ch - [int][char]'A' + 1)
    }
    return $idx - 1
}

$outLines = New-Object System.Collections.Generic.List[string]
$isFirstRow = $true
foreach ($row in $rows) {
    if ($isFirstRow) { $isFirstRow = $false; continue }  # sare peste randul de antet
    $values = New-Object string[] $colCount
    foreach ($c in $row.ChildNodes) {
        $ref = $c.GetAttribute("r")
        if ([string]::IsNullOrEmpty($ref)) { continue }
        $idx = Get-ColumnIndex $ref
        if ($idx -lt 0 -or $idx -ge $colCount) { continue }
        $t = $c.GetAttribute("t")
        $vNode = $c.SelectSingleNode("s:v", $ns)
        if (-not $vNode) { continue }
        $raw = $vNode.InnerText
        if ($t -eq "s") {
            $values[$idx] = $sharedStrings[[int]$raw]
        } else {
            $values[$idx] = $raw
        }
    }
    for ($i = 0; $i -lt $colCount; $i++) {
        if ($null -eq $values[$i]) { $values[$i] = "" }
        # curata tab-uri/newline-uri accidentale din text (siguranta, nu ar trebui sa existe)
        $values[$i] = $values[$i] -replace "`t", " " -replace "`r?`n", " "
    }
    $outLines.Add(($values -join "`t"))
}

Write-Host "Scriu $outGz ..."
if (-not (Test-Path $assetsDir)) { New-Item -ItemType Directory -Path $assetsDir | Out-Null }
if (Test-Path $outGz) { Remove-Item $outGz -Force }
$fs = [System.IO.File]::Create($outGz)
$gz = New-Object System.IO.Compression.GZipStream($fs, [System.IO.Compression.CompressionMode]::Compress)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$writer = New-Object System.IO.StreamWriter($gz, $utf8NoBom)
foreach ($line in $outLines) { $writer.WriteLine($line) }
$writer.Flush()
$writer.Close()

Write-Host "Gata: $($outLines.Count) randuri scrise in $outGz"
Remove-Item $tmpXlsx -Force
