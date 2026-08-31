# Executor gratuito por CI

A documentação oficial do GitHub informa que runners Linux padrão são gratuitos em repositórios públicos. No plano GitHub Free, repositórios privados têm 2.000 minutos por mês e 500 MB compartilhados entre artifacts e GitHub Packages; o limite de artifacts padrão é de até 90 dias, podendo ser reduzido. O upload-artifact atual recomenda versões v4 ou superiores e expõe digest SHA-256. O setup-android oficial instala command-line tools, aceita licenças e suporta configuração com JDK 17 e Gradle.

Fontes consultadas:

1. https://docs.github.com/en/billing/concepts/product-billing/github-actions
2. https://docs.github.com/en/actions/concepts/billing-and-usage
3. https://github.com/actions/upload-artifact
4. https://github.com/android-actions/setup-android

Decisão: usar GitHub Actions como executor sob demanda é viável sem máquina persistente, especialmente em repositório público. Para repositório privado, é necessário controlar o consumo mensal e a retenção dos APKs. O APKForge ainda precisa de uma ponte segura para disparar o workflow e recuperar o artifact sem expor tokens no navegador.
