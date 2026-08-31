# APKForge Android Executor

Este diretório contém o executor Android próprio. Ele deve rodar como um serviço separado da aplicação web, em uma máquina persistente ou em um runner privado com Android SDK, JDK e Gradle instalados. A aplicação web cria o job e o worker consulta a fila por um contrato autenticado; o worker nunca recebe credenciais de usuário nem executa comandos enviados pela origem.

## Arquitetura

| Componente | Responsabilidade | Regra de isolamento |
| --- | --- | --- |
| API APKForge | valida origem, grava conversão e expõe job pendente | aceita callbacks somente com assinatura HMAC |
| Storage S3 | guarda fonte, ícone, projeto intermediário e APK | banco guarda apenas metadados e chaves |
| Worker Android | baixa uma entrada validada, monta o template, executa Gradle e envia o APK | um diretório temporário e usuário sem privilégios por job |
| Notificador | informa conclusão ou falha ao usuário | nunca inclui URLs privadas ou tokens no corpo da mensagem |

## Contrato de job v1

O payload mínimo de submissão é:

```json
{
  "jobId": "integer",
  "sourceType": "url|html|zip|pwa",
  "sourceUrl": "https://example.com",
  "sourceKey": "jobs/123/source.zip",
  "iconKey": "jobs/123/icon.png",
  "appName": "NEON APP",
  "description": "Aplicativo Android",
  "packageId": "com.example.app",
  "version": "1.0.0",
  "openMode": "webview|local",
  "attempt": 1
}
```

O worker deve publicar eventos idempotentes em `queued`, `validating`, `building`, `signing`, `completed` ou `failed`. Cada callback deve conter `jobId`, `status`, `progress`, `message`, `attempt`, `artifactKey` opcional e `sha256` opcional. A API rejeita callbacks com timestamp fora da janela de tolerância, assinatura inválida, transição impossível ou tentativa antiga.

## Pipeline seguro

A entrada deve ser baixada por chave de storage, nunca por caminho de arquivo fornecido pelo usuário. Para URL, o worker deve aceitar apenas HTTPS, bloquear IPs privados e seguir no máximo três redirecionamentos. Para ZIP, é obrigatório validar extensão, tamanho total, número de arquivos, tamanho expandido, traversal (`../`) e links simbólicos antes de extrair. O conteúdo local deve conter `index.html`; PWA deve conter um manifest válido e uma página de entrada.

Cada job usa um diretório temporário com permissões restritas. O worker encerra o subprocesso Gradle em timeout, remove o diretório em `finally`, não executa scripts da origem e copia somente o APK produzido pelo template. O APK é verificado por existência, tamanho mínimo e hash antes de ser enviado ao storage.

## Requisitos do host

Instale JDK 17, Android SDK command-line tools, `platform-tools`, `platforms;android-35`, `build-tools;35.0.0` e Gradle compatível com o template. A chave de assinatura de teste deve ficar fora do repositório, montada como secret somente para o processo do worker. Em produção, use uma fila persistente, limite de concorrência e uma conta de sistema sem acesso administrativo.

## Variáveis de ambiente

```bash
APKFORGE_API_URL=https://seu-app.manus.space
APKFORGE_WORKER_TOKEN=gere-um-token-longo-e-aleatorio
APKFORGE_CALLBACK_SECRET=segredo-compartilhado-para-hmac
APKFORGE_STORAGE_PREFIX=apkforge/jobs
ANDROID_SDK_ROOT=/opt/android-sdk
JAVA_HOME=/usr/lib/jvm/java-17-openjdk
WORKER_CONCURRENCY=1
BUILD_TIMEOUT_MS=900000
```

## Ligação ao APKForge

Os segredos `APKFORGE_WORKER_TOKEN` e `APKFORGE_CALLBACK_SECRET` já foram provisionados no projeto APKForge. No host do executor, injete os mesmos nomes por secret manager ou variáveis de ambiente; nunca copie valores para o repositório, para imagens públicas ou para logs. O worker só deve ser iniciado depois de confirmar que ambos estão presentes e que o endpoint interno está acessível pela rede privada ou por uma allowlist restrita.

## Operação

Execute o worker em um serviço persistente com reinício automático. Antes de liberar tráfego, valide que o token nunca aparece em logs, que o callback exige HMAC, que jobs falhos limpam seus diretórios e que downloads usam URLs assinadas com expiração. O executor foi separado para permitir que a aplicação web continue stateless e que a máquina Android seja dimensionada independentemente.
