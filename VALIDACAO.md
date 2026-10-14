> **ARQUIVO HISTÓRICO (versão 0.2.0).** Para a versão atual 0.3.x, consulte [docs/ESTADO_ATUAL.md](docs/ESTADO_ATUAL.md). Os testes descritos abaixo não certificam o HEAD atual.

# Validação 0.2.0

Versão Android `0.2.0`, versionCode 5, pacote `com.centraldocasal.app`. O agente principal confirmou build, testes atuais e assinatura. O APK 0.2.0 foi atualizado no mesmo link do Drive. A leitura posterior confirmou tamanho de 17.990.136 bytes e a pasta de destino. O aviso de atualização versionCode 5 e o SHA-256 também foram verificados no Firebase.

## Testes e build

Nesta rodada, **90 testes passaram sem falhas**: 74 Android/JVM e 16 de preparação de importações. O build final registrou `BUILD SUCCESSFUL`.

As regras Firestore não mudaram. Os **37 testes de regras aprovados na 0.1.3** continuam como referência da mesma fonte publicada, cuja leitura posterior foi conferida. Esses 37 testes **não foram executados novamente nesta rodada**; não fazem parte dos 90 testes atuais.

## APK verificado

Tamanho: **17.990.136 bytes**. A verificação de assinatura passou e confirmou o mesmo certificado da versão anterior.

SHA-256 do APK:

```text
644073FA529615D85E43F7311A479E9F6FBCFC51240BF32E12AAD4FB0E2C8F3F
```

SHA-256 do certificado:

```text
e9f2540dc0abac177230b518dade031860835ed5f503c278330b3816795a46ef
```

## Recursos conferidos

- Início compacto com tarefas reais, orçamento parcial explícito e atalhos de navegação.
- Editor de metas em tela inteira com calendário, edição e exclusão confirmada. Contribuições anteriores são preservadas para exportação.
- Projetos importados podem iniciar um formulário de nova meta por ação explícita. O valor-alvo começa vazio, etapas sugeridas começam pendentes e o original é preservado; nada é concluído ou confirmado automaticamente.
- Etapas persistidas com revisão esperada; editar detalhes preserva etapas. Progresso deriva de contribuições reais.
- Quantidade/preço inline no Modo Mercado e comparação com histórico do mesmo SKU/unidade, sem vínculo ambíguo.
- Resumo do casal e estrelas que selecionam memórias compartilhadas existentes; experiências exigem duas confirmações.
- OMX oficial usado para diagnóstico, AST Kotlin e registro de revisão; especialistas nativos Codex, sem workers OMX/tmux. CRG executado como revisão estrutural, sem substituir testes Android.

## Limites e entrega

Instalação física, teclado, TalkBack, fontes grandes, notificações e sincronização em dois aparelhos ainda precisam de validação em hardware. Não há certificação de SOS, rastreamento ou versão integral 1.0.

APK e documentação da 0.2.0 aguardam atualização no Drive. O envio opcional de ZIP de fonte continua aguardando aprovação explícita e não foi realizado. Dados pessoais, UIDs, propostas privadas completas, credenciais administrativas, logs e caches não pertencem ao pacote público.

