<#
  Sobe tudo para teste local: API, OCR, facial e painel, cada um na sua janela.
  Uso (na pasta do repositorio):
      .\iniciar-tudo.ps1               # sobe os 4
      .\iniciar-tudo.ps1 -SemServicos  # so API e painel (testa login e telas)
  Na primeira vez cria o arquivo .env (ignorado pelo git) com chaves aleatorias.
  Para parar: feche as janelas abertas.
#>
param([switch]$SemServicos)

$ErrorActionPreference = 'Stop'
$raiz = $PSScriptRoot
$envFile = Join-Path $raiz '.env'

function Novo-Base64([int]$bytes) {
    $b = New-Object byte[] $bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    [Convert]::ToBase64String($b)
}
function Novo-Hex([int]$bytes) {
    $b = New-Object byte[] $bytes
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
    -join ($b | ForEach-Object { $_.ToString('x2') })
}

# 1) Primeira vez: cria o .env a partir do .env.example, com chaves aleatorias.
if (-not (Test-Path $envFile)) {
    Write-Host 'Primeira execucao: criando o arquivo .env (fica so no seu computador).'
    $senhaBanco = Read-Host 'Senha do usuario fast_checkin no PostgreSQL'
    $valores = @{
        DB_PASSWORD                  = $senhaBanco
        PERSONAL_DATA_ENCRYPTION_KEY = Novo-Base64 32
        JWT_SECRET                   = Novo-Base64 32
        ADMIN_USERNAME               = 'admin'
        ADMIN_PASSWORD               = Novo-Hex 8
        OCR_API_KEY                  = Novo-Hex 16
        FACIAL_API_KEY               = Novo-Hex 16
    }
    $saida = foreach ($linha in Get-Content (Join-Path $raiz '.env.example')) {
        if ($linha -match '^([A-Z_]+)=') {
            $chave = $Matches[1]
            if ($valores.ContainsKey($chave)) { "$chave=$($valores[$chave])"; continue }
        }
        $linha
    }
    [IO.File]::WriteAllLines($envFile, $saida, (New-Object Text.UTF8Encoding($false)))
    Write-Host "Pronto. Login do painel: usuario admin, senha $($valores.ADMIN_PASSWORD) (tambem esta no .env)."
    Write-Host 'Atencao: nao apague nem troque a PERSONAL_DATA_ENCRYPTION_KEY depois, ou os dados ja gravados deixam de abrir.'
}

# 2) Carrega o .env nesta sessao (as janelas novas herdam as variaveis).
foreach ($linha in Get-Content $envFile) {
    if ($linha -match '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
foreach ($obrigatoria in 'DB_PASSWORD', 'PERSONAL_DATA_ENCRYPTION_KEY', 'JWT_SECRET', 'ADMIN_USERNAME', 'ADMIN_PASSWORD') {
    if (-not [Environment]::GetEnvironmentVariable($obrigatoria, 'Process')) {
        throw "A variavel $obrigatoria esta vazia no .env. Preencha e rode de novo."
    }
}

function Abrir-Janela([string]$titulo, [string]$pasta, [string]$comando) {
    $cmd = "`$Host.UI.RawUI.WindowTitle = '$titulo'; $comando"
    Start-Process powershell -WorkingDirectory $pasta -ArgumentList '-NoExit', '-Command', $cmd | Out-Null
    Write-Host "Aberta: $titulo"
}

# 3) API Java
Abrir-Janela 'API Fast Check-in' $raiz 'mvn spring-boot:run'

# 4) Servicos Python (precisam do .venv de cada pasta, criado como nos README)
if (-not $SemServicos) {
    foreach ($s in @(@('OCR', 'ocr-servico', 8001), @('Facial', 'facial-servico', 8002))) {
        $pasta = Join-Path $raiz $s[1]
        $py = Join-Path $pasta '.venv\Scripts\python.exe'
        if (Test-Path $py) {
            Abrir-Janela "Servico $($s[0])" $pasta ".\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port $($s[2])"
        } else {
            Write-Warning "Sem .venv em $($s[1]). Crie como no README da pasta; o servico $($s[0]) nao foi iniciado."
        }
    }
}

# 5) Painel (instala as dependencias na primeira vez)
$painel = Join-Path $raiz 'painel-fast-checkin'
if (Test-Path $painel) {
    Abrir-Janela 'Painel' $painel 'if (-not (Test-Path node_modules)) { npm install }; npm run dev'
    Write-Host 'Quando o painel estiver pronto, abra http://localhost:5173 (a API leva cerca de 30 s para subir).'
} else {
    Write-Warning 'Pasta painel-fast-checkin nao encontrada.'
}
