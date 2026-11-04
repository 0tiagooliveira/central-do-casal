---
name: central-animation-runtime
description: Decide e integra recursos animados Android com segurança, escolhendo entre Compose, Lottie e Rive conforme necessidade e licença.
---

# Escolha de runtime de animação

Fontes:
- https://lottiefiles.com/
- https://github.com/LottieFiles/motion-design-skill
- https://rive.app/
- https://rive.app/docs/runtimes/android/android
- https://github.com/airbnb/lottie-android

## Matriz
- Compose animates: navegação, expansão de cards, feedback, barras de progresso; primeira opção.
- Lottie Compose: animação vetorial curta, sem estado interativo complexo, por exemplo confirmação de tarefa.
- Rive Android Compose: ilustração que muda conforme entradas/estados, por exemplo avatar da Sophia; adotar só após um protótipo funcional.

## Passos antes de integrar biblioteca
1. Selecionar um caso de uso real aprovado e seu estado estático alternativo.
2. Verificar licença do runtime, do arquivo .json/.lottie/.riv e dos elementos gráficos (licença do player não licencia a arte).
3. Fixar versão exata e revisar tamanho de APK, startup, memória e segurança da dependência; documentar decisão.
4. Pré-carregar e armazenar assets locais confiáveis; evitar downloads arbitrários e scripts externos em produção.
5. Definir evento de início/fim, cancelamento e acessibilidade. Nunca autoplay infinito na Home.
6. Comprovar execução em S23 FE e S25 FE, fonte grande, economia de bateria, perda de rede e movimento reduzido.
7. Se não existir recurso aprovado, não modificar build.gradle nem adicionar dependência. Apenas documentar o protótipo.

## Não confundir skill com runtime
As skills orientam agentes no desenvolvimento. Elas não são instaladas no APK; Rive/Lottie são bibliotecas distintas para exibição de animação.
