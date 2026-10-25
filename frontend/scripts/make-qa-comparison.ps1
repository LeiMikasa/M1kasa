Add-Type -AssemblyName System.Drawing
$sourcePath = Join-Path $PSScriptRoot '..\design-source.png'
$implementationPath = Join-Path $PSScriptRoot '..\implementation-mobile-screen.png'
$outputPath = Join-Path $PSScriptRoot '..\design-comparison.png'
$source = [System.Drawing.Image]::FromFile($sourcePath)
$implementation = [System.Drawing.Image]::FromFile($implementationPath)
$canvas = New-Object System.Drawing.Bitmap(820, 890)
$graphics = [System.Drawing.Graphics]::FromImage($canvas)
try {
    $graphics.Clear([System.Drawing.Color]::FromArgb(245, 241, 237))
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($source, 8, 29, 393, 852)
    $graphics.DrawImage($implementation, 419, 29, 393, 852)
    $font = New-Object System.Drawing.Font('Arial', 10)
    $brush = [System.Drawing.Brushes]::Black
    $graphics.DrawString('Selected design', $font, $brush, 8, 7)
    $graphics.DrawString('Implementation', $font, $brush, 419, 7)
    $canvas.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $graphics.Dispose()
    $canvas.Dispose()
    $source.Dispose()
    $implementation.Dispose()
}
Write-Output $outputPath
