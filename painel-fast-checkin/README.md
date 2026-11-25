# Painel da recepção · Fast Check-in

React + JavaScript + Tailwind CSS (Vite). Conversa com a API Java pela rota `/api`, usando um proxy do Vite.

## Como rodar

1. Suba a API Java com as variáveis de login definidas (além das de banco e criptografia):
   ```powershell
   $env:ADMIN_USERNAME = "admin"
   $env:ADMIN_PASSWORD = "uma-senha-forte"
   # JWT_SECRET precisa ser uma chave em Base64 com pelo menos 32 bytes. Gere uma:
   $b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
   $env:JWT_SECRET = [Convert]::ToBase64String($b)
   mvn spring-boot:run
   ```
2. Neste projeto (Node 18 ou superior):
   ```powershell
   npm install
   npm run dev
   ```
3. Abra http://localhost:5173 e entre com o usuário e a senha acima.

Se a API estiver em outra porta, mude o destino do proxy em `vite.config.js`.

## O que o painel faz
- Login com token (perfis Administrador e Recepcionista).
- Hóspedes: listar, buscar, cadastrar (com consentimento obrigatório) e excluir.
- Estadias: criar e excluir.
- Fichas FNRH: fila **Para revisar** (marcadas pela leitura do documento), confirmar pelo endpoint `/confirm`, criar e excluir. A lista se atualiza a cada 10 s (pausa com a aba escondida), então os check-ins do totem aparecem sozinhos.
- **Resultado do rosto (M17):** a tabela de fichas tem a coluna **Rosto** (Compatível, Revisar ou Diferente, ou "Não verificado"). Em Revisar ou Diferente, o recepcionista confere o hóspede pessoalmente e usa **Confirmar rosto**. Confirmar a ficha avisa quando o rosto ainda não foi conferido. O painel não captura nem envia fotos: o documento e a selfie são do totem, e a API guarda só o resultado.
- Administrador: tela **Usuários** (crie um usuário só para o totem) e tela **Auditoria** (quem leu ou alterou dados pessoais, e rodar a retenção).
- Hotéis: listar; administradores adicionam e excluem.

Documentos fictícios apenas. Não use dados reais nos testes.

## Limites
- O resultado do rosto só aparece depois que o totem (ou outro cliente) chamar a comparação facial da API. A comparação precisa do `facial-servico` rodando.
- O token de login fica na sessão do navegador (`sessionStorage`) e some ao fechar a aba.
- A lista de hóspedes mostra CPF e documento mascarados.
