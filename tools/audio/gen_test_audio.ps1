Add-Type -AssemblyName System.Speech
$outDir = Join-Path $PSScriptRoot "clips"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$lines = @(
  @{ id = "01_en"; voice = "Microsoft Zira Desktop"; text = "Good morning, everyone. Thanks for joining on such short notice." },
  @{ id = "02_zh"; voice = "Microsoft Huihui Desktop"; text = "早上好，我们今天主要讨论新产品的发布计划。" },
  @{ id = "03_en"; voice = "Microsoft Zira Desktop"; text = "I think we should move the launch to early next month." },
  @{ id = "04_zh"; voice = "Microsoft Huihui Desktop"; text = "我同意，不过市场团队需要更多时间准备材料。" },
  @{ id = "05_en"; voice = "Microsoft Zira Desktop"; text = "Then let us give them two extra weeks and keep the budget unchanged." },
  @{ id = "06_zh"; voice = "Microsoft Huihui Desktop"; text = "好的，我下午会把最终的时间表发给大家。" },
  @{ id = "07_en"; voice = "Microsoft Zira Desktop"; text = "Great. One more thing, the customer demo is on Friday." },
  @{ id = "08_zh"; voice = "Microsoft Huihui Desktop"; text = "没问题，我会安排技术团队提前测试。" }
)

$fmt = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
  16000,
  [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
  [System.Speech.AudioFormat.AudioChannel]::Mono)

foreach ($line in $lines) {
  $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
  $synth.SelectVoice($line.voice)
  $synth.Rate = 0
  $path = Join-Path $outDir ($line.id + ".wav")
  $synth.SetOutputToWaveFile($path, $fmt)
  $synth.Speak($line.text)
  $synth.Dispose()
  Write-Host "wrote $path"
}
