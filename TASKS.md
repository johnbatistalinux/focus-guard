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

## Milestone 6: Pacote de Reforço Positivo & Fricção Consciente
- [x] Task 6.1: [Contract] Expandir `FocusConfig`, `AppUsageState`, `FocusRewardState`, `FocusRewardSummary` e `InterventionAction`
- [x] Task 6.2: [Engine] Implementar Pausa Progressiva (+5s/sessão), Guia de Respiração 1 Hz, Micro-Sessão de 1 min, Bônus de Saída Voluntária e Consistência em Bitmask
- [x] Task 6.3: [Data] Persistir métricas de reforço positivo e rollover diário da máscara de consistência em `FocusPreferencesStore`
- [x] Task 6.4: [Overlay & UI] Adicionar celebração háptica na desistência, âncora de hábito, pílula interativa (troca de lado + saída voluntária) e Card de Conquistas na `MainActivity`
- [x] Task 6.5: [Test] Cobrir o pacote de reforço positivo na suíte unitária JUnit 5

## Milestone 7: v1.2.0 — Otimização Extrema, Polimento & Correção de Bugs
- [x] Task 7.1: [Bugfix] Pausar sessão imediatamente ao desligar a tela (`PowerManager.isInteractive` + `onScreenTurnedOff`)
- [x] Task 7.2: [Bugfix] Expandir filtro de pacotes transitórios (`SwiftKey`, `Honeyboard`, `PermissionController`) e creditar saída voluntária quando sessão pausada expira em background
- [x] Task 7.3: [Perf] Implementar `BatchEditor` (`editBatch`), cache em memória para `AppUsageState`/`FocusRewardState` e singleton `FocusRuntime` compartilhado no processo
- [x] Task 7.4: [Perf] Eliminar alocações de `GradientDrawable` e `Color.parseColor` no tick de 1 Hz do `OverlayController`
- [x] Task 7.5: [Build] Migrar `compilerOptions` para Kotlin 2.0 DSL e habilitar compilação Release minificada com R8 (`assembleRelease`)
