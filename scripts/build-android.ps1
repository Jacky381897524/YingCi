param(
    [string[]]$Tasks = @('assembleRelease','testDebugUnitTest','lintRelease'),
    [string]$BuildToolsPath = ''
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$candidates = @(
    $BuildToolsPath,
    (Join-Path $projectRoot '.tools'),
    (Join-Path (Split-Path -Parent $projectRoot) 'Huitu skill/.tools')
)
$toolsDirectory = $candidates | Where-Object {
    $_ -and (Test-Path (Join-Path $_ 'jdk')) -and
    (Test-Path (Join-Path $_ 'gradle/gradle-8.11.1/bin/gradle.bat')) -and
    (Test-Path (Join-Path $_ 'sdk/build-tools/35.0.0/zipalign.exe'))
} | Select-Object -First 1
if (-not $toolsDirectory) { throw 'Android build tools not found. Install JDK 17, Gradle 8.11.1 and Android SDK 35 under .tools or specify -BuildToolsPath.' }
$drives = @('Y:','X:','W:','V:') | Where-Object { -not (Test-Path "$_\") } | Select-Object -First 2
if ($drives.Count -lt 2) { throw 'Two unused drive letters are required by the Android build tools.' }
try {
    & subst.exe $drives[0] $projectRoot
    if ($LASTEXITCODE -ne 0) { throw 'Project path mapping failed' }
    & subst.exe $drives[1] $toolsDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Build tools path mapping failed' }
    $mappedProject = "$($drives[0])/"
    $toolsRoot = "$($drives[1])/"
    $env:JAVA_HOME = (Get-ChildItem "$toolsRoot/jdk" -Directory | Select-Object -First 1).FullName
    $env:ANDROID_HOME = "$toolsRoot/sdk"
    $env:ANDROID_USER_HOME = "$mappedProject.tools/android-user"
    $env:GRADLE_USER_HOME = "$toolsRoot/gradle-user"
    $sdkPath = $env:ANDROID_HOME.Replace(':','\:')
    [IO.File]::WriteAllText("$mappedProject/android/local.properties", "sdk.dir=$sdkPath`n", [Text.UTF8Encoding]::new($false))
    Push-Location "$mappedProject/android"
    try {
        & "$toolsRoot/gradle/gradle-8.11.1/bin/gradle.bat" @Tasks --console=plain --no-daemon --continue
        if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
    } finally { Pop-Location }
    if ($Tasks -contains 'assembleRelease') {
        $apk = "$mappedProject/android/app/build/outputs/apk/release/app-release.apk"
        & "$env:JAVA_HOME/bin/java.exe" -jar "$toolsRoot/sdk/build-tools/35.0.0/lib/apksigner.jar" verify --verbose $apk
        if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
        & "$toolsRoot/sdk/build-tools/35.0.0/zipalign.exe" -c -P 16 4 $apk
        if ($LASTEXITCODE -ne 0) { throw 'APK alignment verification failed' }
        $metadata = Get-Content -LiteralPath "$mappedProject/android/app/build/outputs/apk/release/output-metadata.json" -Raw | ConvertFrom-Json
        $version = $metadata.elements[0].versionName
        if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid release version' }
        $name = "YingCi-v$version.apk"
        Copy-Item -LiteralPath $apk -Destination "$projectRoot/output/$name" -Force
        $hash = (Get-FileHash "$projectRoot/output/$name" -Algorithm SHA256).Hash
        [IO.File]::WriteAllText("$projectRoot/output/SHA256.txt", "$hash  $name`n")
    }
} finally { foreach ($drive in $drives) { & subst.exe $drive /D } }
