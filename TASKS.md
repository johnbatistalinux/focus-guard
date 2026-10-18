# Task Progress & Execution Matrix — FocusGuard

## Milestone 1: Bootstrap & Domínio Puro (Core Engine & Contratos)
- [x] Task 1.1: [Docs] Definir `PRODUCT.md`, `ARCHITECTURE.md` e `TASKS.md` (Triad Source of Truth)
- [x] Task 1.2: [Scaffold] Configurar estrutura Gradle Kotlin DSL enxuta (Zero bibliotecas externas de runtime, apenas JUnit para testes na JVM)
- [x] Task 1.3: [Contract] Implementar modelos de domínio (`SocialAppCatalog`, `FocusConfig`, `InterventionAction`, `TimeProvider`) em `com.focusguard.app.domain`
- [x] Task 1.4: [Test] Escrever suíte de testes unitários para `FocusPolicyEngine` (Pausa consciente, contagem de sessão, pausa ao sair do app, ativação de cooldown, estouro de cota diária, reset na virada do dia e trava anti-sabotagem)
- [x] Task 1.5: [Impl] Implementar `FocusPolicyEngine` passando 100% nos testes unitários

## Milestone 2: Persistência Ultraleve (`SharedPreferences`)
- [x] Task 2.1: [Contract & Impl] Implementar `FocusPreferencesStore` com serialização leve de conjuntos de pacotes, tempo acumulado por dia, timestamps de cooldown e *lazy reset* por `epochDay`
- [x] Task 2.2: [Test] Validar persistência e virada de dia automática nos testes de domínio

## Milestone 3: Motor de Acessibilidade & Overlays Nativos (`TYPE_ACCESSIBILITY_OVERLAY`)
- [x] Task 3.1: [Impl] Implementar `MainThreadTicker` usando `Handler(Looper.getMainLooper())` sem alocação de coroutines
- [x] Task 3.2: [Impl] Implementar `OverlayController` com Views nativas programáticas:
  - Tela de Pausa Consciente (contagem regressiva de 10s + botão de desistência imediata + botão de liberar sessão)
  - Pílula Flutuante de Sessão (cronômetro compacto no topo da tela durante os 5 min liberados)
  - Tela de Bloqueio Estrito (Cooldown de 10 min ou Cota Diária esgotada)
- [x] Task 3.3: [Impl] Implementar `FocusAccessibilityService` integrando `FocusPolicyEngine`, `OverlayController`, atualização dinâmica de `serviceInfo.packageNames` e detecção de saída para o Launcher

## Milestone 4: Tela de Configuração & Trava Anti-Sabotagem
- [x] Task 4.1: [Impl] Implementar `MainActivity` (herdando de `android.app.Activity` pura) com:
  - Indicador de status do Serviço de Acessibilidade + atalho para `Settings.ACTION_ACCESSIBILITY_SETTINGS`
  - Lista de switches para as redes sociais do `SocialAppCatalog`
  - Seletor dos parâmetros (Tempo de pausa, duração da sessão, cooldown e cota diária)
  - Banner e bloqueio de edição (Modo Somente Leitura) quando a trava anti-sabotagem estiver ativa
- [x] Task 4.2: [Config] Configurar `AndroidManifest.xml` e `res/xml/accessibility_service_config.xml` (sem permissão de internet, com R8 ativado no release)

## Milestone 5: Verificação de Qualidade (`pre-flight`) & Guia de Build APK
- [x] Task 5.1: [Gate] Rodar testes unitários e validação de compilação/lint
- [x] Task 5.2: [Docs] Adicionar instruções rápidas de instalação via `adb install` ou Android Studio
